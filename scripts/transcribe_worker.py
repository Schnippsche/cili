#!/usr/bin/env python3
"""
faster-whisper CLI-Wrapper für CILI.
Verarbeitet eine WAV-Datei und schreibt eine VTT-Datei.

Cue-Algorithmus (aus trans.py übernommen):
  * Cues werden direkt aus Wort-Zeitstempeln gebaut (Greedy mit Grenzen bei
    Satzende, Zeichenlimit, Cue-Dauer und Sprechpause) — kein wtpsplit mehr.
  * merge_short_cues verschmilzt Waisen-Cues (Einzelwörter / sehr kurz).
  * Sync-Korrektur skaliert die Zeitstempel linear auf die Wiedergabedauer,
    falls die dekodierte Audiospur vom Video-Takt abweicht (Clock-Mismatch).
    Bei reinen Audioquellen (WAV/mp3) ist das ein No-Op. Für eine aus Video
    extrahierte WAV das Originalvideo via --sync-ref angeben.

Aufruf:
  python transcribe_worker.py --wav /path/to/audio.wav --output /path/to/out.vtt
                               [--lang de] [--model large-v3]
                               [--device cuda] [--compute-type int8_float16]
                               [--beam-size 5]
                               [--glossary /path/to/glossar.txt]
                               [--corrections /path/to/corrections.txt]
                               [--sync video|container|off] [--sync-ref video.mp4]
                               [--time-scale 0.99491]
"""

import argparse
import glob
import json
import os
import re
import subprocess
import sys
from difflib import SequenceMatcher

os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")
os.environ.setdefault("TRANSFORMERS_VERBOSITY", "error")

# Windows: CUDA-DLL-Verzeichnisse VOR dem faster-whisper-/ctranslate2-Import registrieren.
# Zwei Quellen: pip-installierte nvidia-Pakete UND das NVIDIA CUDA Toolkit.
if sys.platform == "win32":
    import site as _site_mod
    # 1. pip-nvidia-Pakete (z.B. nvidia-cublas-cu12)
    for _site in _site_mod.getsitepackages():
        _nvidia = os.path.join(_site, "nvidia")
        if os.path.isdir(_nvidia):
            for _sub in os.listdir(_nvidia):
                _bin = os.path.join(_nvidia, _sub, "bin")
                if os.path.isdir(_bin):
                    os.environ["PATH"] = _bin + os.pathsep + os.environ.get("PATH", "")
                    try:
                        os.add_dll_directory(_bin)
                    except OSError:
                        pass
    # 2. System-CUDA-Toolkit (beliebige Version unter Program Files)
    for _cuda_bin in glob.glob(
        r"C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v*\bin"
    ):
        os.environ["PATH"] = _cuda_bin + os.pathsep + os.environ.get("PATH", "")
        try:
            os.add_dll_directory(_cuda_bin)
        except OSError:
            pass

# --- Modul-Level Regex-Kompilierung ---

# Problem 2: weniger aggressiv — nur ein einzelnes optionales Leerzeichen überbrücken
# und nie mitten in einem anderen Wort beginnen (damit Versionsangaben wie
# "x1 . 234" unberührt bleiben).
_RE_THOUSANDS_SINGLE = re.compile(r'(?<!\w)(\d+)\s?\.\s?(\d{3})\b')
_RE_THOUSANDS_MULTI  = re.compile(r'(?<!\w)\d{1,3}(?:\s?\.\s?\d{3})+')

# Für is_protected_number (heiße Schleife: einmal kompilieren)
_RE_DATE             = re.compile(r'^\d{1,2}\.\d{1,2}(\.\d{2,4})?$')
_RE_DECIMAL          = re.compile(r'^\d+[\.,]\d+$')
_RE_THOUSANDS_FULL   = re.compile(r'^\d{1,3}(\.\d{3})+$')

PROTECTED_ABBREVIATIONS = {
  "bzw.", "z.b.", "z. b.", "u.a.", "u. a.", "usw.", "ca.", "etc.",
  "dr.", "prof.", "nr.", "st.", "str.", "geb.", "hr.", "fr.", "fa."
}

_SENTENCE_END = ('.', '!', '?', '…')


def gpu_temperature() -> str:
  """Liest die aktuelle GPU-Temperatur über nvidia-smi aus (z.B. '75'). Liefert '?' falls nicht verfügbar."""
  try:
    out = subprocess.run(
      ['nvidia-smi', '--query-gpu=temperature.gpu', '--format=csv,noheader,nounits'],
      capture_output=True, text=True, timeout=5, check=True,
    )
    return out.stdout.strip().splitlines()[0]
  except Exception:
    return '?'


def load_corrections(path: str):
  """Lädt Korrekturen und trennt sie in Einzelwort- und Phrasen-Regeln.

  Rückgabe: (single, phrase)
    single: [(pattern, replacement), ...]             — 'wrong' ohne Leerzeichen
    phrase: [(pattern, replacement, word_count), ...] — 'wrong' mit Leerzeichen
  Beide Listen sind nach Länge des Suchmusters absteigend sortiert, damit
  längere/spezifischere Treffer vor ihren Teil-Wörtern greifen."""
  if not path or not os.path.exists(path):
    return [], []
  single = []
  phrase = []
  skipped = []
  with open(path, "r", encoding="utf-8") as f:
    for lineno, line in enumerate(f, 1):
      line = line.strip()
      if not line or line.startswith("#"):
        continue
      if ":" not in line:
        skipped.append(lineno)
        continue
      correct, _, wrongs_raw = line.partition(":")
      correct = correct.strip()
      wrongs = [w.strip() for w in wrongs_raw.split(",") if w.strip()]
      for wrong in wrongs:
        pattern = re.compile(r'\b' + re.escape(wrong) + r'\b', re.IGNORECASE)
        if " " in wrong:
          phrase.append((pattern, correct, len(wrong.split())))
        else:
          single.append((pattern, correct))
  if skipped:
    print(f"[whisper] Warnung: {len(skipped)} fehlerhafte Zeilen in {path} "
          f"(kein ':'-Trenner) — Zeilen: {skipped[:10]}", file=sys.stderr, flush=True)
  single.sort(key=lambda c: len(c[0].pattern), reverse=True)
  phrase.sort(key=lambda c: len(c[0].pattern), reverse=True)
  return single, phrase


def normalize_thousands(text: str) -> str:
  text = _RE_THOUSANDS_SINGLE.sub(r'\1.\2', text)
  text = _RE_THOUSANDS_MULTI.sub(lambda m: m.group(0).replace(" ", ""), text)
  return text


def is_protected_abbreviation(word: str) -> bool:
  w = word.lower().strip(",;:-")
  return w in PROTECTED_ABBREVIATIONS or (w + ".") in PROTECTED_ABBREVIATIONS


def apply_corrections(text: str, corrections: list) -> str:
  # corrections-Einträge sind (pattern, replacement) oder (pattern, replacement, n);
  # nur die ersten beiden Felder werden gebraucht.
  for c in corrections:
    text = c[0].sub(c[1], text)
  return text


def apply_phrase_corrections_to_words(words, phrase_corrections):
  """Wendet mehrwortige Korrekturen über aufeinanderfolgende Wort-Tokens an,
  damit eine Phrase auch dann ersetzt wird, wenn sie sonst über eine Cue-Grenze
  getrennt würde. Eine getroffene Wortgruppe wird zu einem einzigen Token
  zusammengefasst (Start des ersten, Ende des letzten Wortes), sodass sie beim
  späteren Cue-Bau nicht mehr auseinandergerissen werden kann."""
  if not phrase_corrections:
    return words
  for pattern, replacement, n in phrase_corrections:
    out = []
    i = 0
    while i < len(words):
      window = words[i:i + n]
      if len(window) == n:
        joined = " ".join(w[0] for w in window)
        new_joined, count = pattern.subn(replacement, joined)
        if count:
          out.append((new_joined, window[0][1], window[-1][2]))
          i += n
          continue
      out.append(words[i])
      i += 1
    words = out
  return words


def apply_single_corrections_to_words(words, single_corrections):
  """Wendet Einzelwort-Korrekturen auf jedes Token an. Läuft *nach* dem
  Phrasen-Pass, damit eine Phrase, deren Teilwort auch eine Einzelregel ist,
  zuerst als Ganzes ersetzt wird (z. B. 'Sil Design' → 'Cili by Design',
  bevor 'Sil' → 'Cili' greifen kann)."""
  if not single_corrections:
    return words
  return [(apply_corrections(t, single_corrections), s, e) for t, s, e in words]


def similar(a, b, threshold=None):
  """Ähnlichkeit zweier Strings (0..1) via SequenceMatcher.

  Mit gesetztem threshold greift ein Schnell-Vorfilter: real_quick_ratio() und
  quick_ratio() sind günstige Obergrenzen für ratio(). Liegt schon die billige
  Obergrenze unter dem Schwellwert, kann ratio() (das tatsächliche Matching)
  übersprungen werden und es wird 0.0 zurückgegeben — für den >=-Vergleich beim
  Aufrufer äquivalent, aber deutlich schneller."""
  sm = SequenceMatcher(None, a, b)
  if threshold is not None:
    if sm.real_quick_ratio() < threshold or sm.quick_ratio() < threshold:
      return 0.0
  return sm.ratio()


def remove_fuzzy_duplicates(segments, threshold=0.85, max_gap=2.0):
  """Entfernt aufeinanderfolgende Beinahe-Duplikate (Whisper-Halluzinations-Loops).

  Eine Wiederholung wird nur verworfen, wenn sie zeitlich dicht folgt (<= max_gap s).
  Identische Phrasen mit echter Sprechpause dazwischen — z.B. 'Ja.' … 'Ja.' oder
  zweimal 'Vielen Dank.' im Dialog — sind legitim und bleiben erhalten (Problem 5)."""
  cleaned = []
  last_text = ""
  last_end = None
  for seg in segments:
    text = seg.text.strip()
    gap = seg.start - last_end if last_end is not None else None
    # Zuerst die billige Zeitlücken-Prüfung; nur bei kleiner Lücke überhaupt die
    # teurere String-Ähnlichkeit berechnen (and-Kurzschluss).
    if (gap is not None and gap <= max_gap
        and similar(text, last_text, threshold) >= threshold):
      print(f"[duplikat] Entfernt: '{text}' (ähnlich zu '{last_text}', "
            f"Abstand {gap:.2f}s)", flush=True)
      continue
    cleaned.append(seg)
    last_text = text
    last_end = seg.end
  return cleaned


def flatten_words(segments):
  """Wort-Tokens aus allen Whisper-Segmenten zu (text, start, end)-Tupeln
  flachklopfen. Hier passiert nur die Zahlen-Normalisierung; Korrekturen werden
  danach in zwei Pässen angewandt — *Phrasen vor Einzelwörtern* (siehe main()) —
  damit eine Phrase, deren Teilwort auch eine Einzelregel ist, zuerst als Ganzes
  ersetzt wird."""
  words = []
  for seg in segments:
    for w in (seg.words or []):
      text = normalize_thousands(w.word.strip())
      if text:
        words.append((text, w.start, w.end))
  return words


def is_protected_number(word: str) -> bool:
  w = word.lower().rstrip(",;:-")
  return (
    _RE_DATE.match(w) is not None
    or _RE_DECIMAL.match(w) is not None
    or _RE_THOUSANDS_FULL.match(w) is not None
  )


def _is_sentence_end(word: str) -> bool:
  """True, wenn das Wort einen Satz beendet — aber nicht bei Zahlen
  (Datum/Dezimal/Tausender) oder geschützten Abkürzungen."""
  if not word.endswith(_SENTENCE_END) or word in {'.', '!', '?', '…'}:
    return False
  return not is_protected_number(word) and not is_protected_abbreviation(word)


class _CueBuilder:
  """Sammelt Wörter zu einem Cue und schließt ihn bei flush() ab.

  Ausgelagert aus build_cues_from_words, damit die frühere verschachtelte
  flush()-Closure (und ihr nonlocal-State) die Komplexität nicht aufbläht."""

  def __init__(self):
    self.cues = []
    self.cur = []          # Liste von (text, start, end)
    self.start = None
    self.chars = 0

  def add(self, wtext, wstart, wend):
    if self.start is None:
      self.start = wstart
    self.chars += (1 if self.cur else 0) + len(wtext)  # +1 für Trennzeichen
    self.cur.append((wtext, wstart, wend))

  def flush(self, end_time):
    if self.cur:
      text = " ".join(w[0] for w in self.cur).strip()
      if text:
        self.cues.append((self.start, end_time, text))
    self.cur = []
    self.start = None
    self.chars = 0


def build_cues_from_words(words, max_chars=84, max_duration=6.0, max_gap=0.8):
  """Cues direkt aus Wort-Zeitstempeln (text, start, end) bauen.

  Ein neuer Cue beginnt, sobald eine Grenze erreicht ist:
    * Satzende-Interpunktion (geschützt gegen Zahlen/Abkürzungen)
    * Zeichenlimit (max_chars)
    * Cue-Dauer (max_duration)
    * Sprechpause zwischen zwei Worten (max_gap)

  Dadurch kleben die Zeitstempel eng am tatsächlichen Sprechzeitpunkt statt am
  internen 30-s-Decodierfenster. Liefert (start, end, text)-Tupel."""
  b = _CueBuilder()
  prev_end = None

  for wtext, wstart, wend in words:
    gap = (wstart - prev_end) if prev_end is not None else 0.0
    if b.cur and gap > max_gap:        # Sprechpause → laufenden Cue abschließen
      b.flush(prev_end)

    b.add(wtext, wstart, wend)
    prev_end = wend

    too_long = max_chars > 0 and b.chars >= max_chars
    too_dur = max_duration > 0 and (wend - b.start) >= max_duration
    if _is_sentence_end(wtext) or too_long or too_dur:
      b.flush(wend)

  b.flush(prev_end if prev_end is not None else 0.0)
  return b.cues


def _is_short_cue(text, min_chars):
  """True, wenn ein Cue als 'Waise' gilt (zu wenige Zeichen oder Einzelwort)."""
  return len(text) < min_chars or len(text.split()) <= 1


def _cues_mergeable(left, right, max_gap, merge_max):
  """True, wenn right an left angehängt werden darf (kleine Lücke + Längenlimit)."""
  return (right[0] - left[1] <= max_gap
          and len(left[2]) + 1 + len(right[2]) <= merge_max)


def _join_cues(left, right):
  """Verschmilzt zwei (start, end, text)-Cues zu einem."""
  return (left[0], right[1], (left[2] + " " + right[2]).strip())


def _resolve_pending(pending, cur, out, max_gap, merge_max):
  """Verrechnet einen wartenden Cue mit dem aktuellen: entweder verschmelzen
  (Rückgabe = verschmolzener Cue) oder den wartenden nach out übernehmen
  (Rückgabe = unveränderter aktueller Cue)."""
  if _cues_mergeable(pending, cur, max_gap, merge_max):
    return _join_cues(pending, cur)
  out.append(pending)
  return cur


def merge_short_cues(cues, min_chars=25, merge_max=0, max_gap=0.8):
  """Verschmilzt zu kurze / Einzelwort-Cues mit einem Nachbarn.

  Beseitigt 'Waisen' wie ein allein stehendes 'müde.': bevorzugt der
  vorhergehende Cue, sonst der folgende. Verschmolzen wird nur bei kleiner
  Sprechpause (max_gap) und solange der zusammengefügte Text merge_max nicht
  überschreitet. Erwartet/liefert (start, end, text)-Tupel."""
  if min_chars <= 0 or not cues:
    return cues

  out = []
  pending = None  # (start, end, text), wartet auf den nächsten Cue

  for cur in cues:
    if pending is not None:
      cur = _resolve_pending(pending, cur, out, max_gap, merge_max)
      pending = None

    # Nicht-Waise oder kein Vorgänger → einfach übernehmen
    if not (_is_short_cue(cur[2], min_chars) and out):
      out.append(cur)
      continue

    # Waise: bevorzugt rückwärts verschmelzen, sonst auf den nächsten Cue warten
    if _cues_mergeable(out[-1], cur, max_gap, merge_max):
      out[-1] = _join_cues(out[-1], cur)
    else:
      pending = cur

  if pending is not None:
    out.append(pending)
  return out


def cues_from_segments(segments):
  """Fallback ohne Wort-Zeitstempel: Segment == Cue (zeitlich unangetastet)."""
  cues = []
  for seg in segments:
    text = seg.text.strip()
    if text:
      cues.append((seg.start, seg.end, text))
  return cues


def add_cue_padding(cues, pad=0.2):
  """Hängt jedem Cue-Ende einen kurzen Nachlauf an, damit Untertitel nicht
  verschwinden, sobald das letzte Wort endet (Problem 3). Der Nachlauf wird auf
  den Start des nächsten Cues begrenzt (kein Überlappen) und verkürzt nie einen
  Cue."""
  result = []
  for i, (start, end, text) in enumerate(cues):
    new_end = end + pad
    if i + 1 < len(cues):
      new_end = min(new_end, cues[i + 1][0])
    new_end = max(new_end, end)  # nie unter das echte Ende ziehen (überlappende Eingabe)
    result.append((start, new_end, text))
  return result


# --------------------------------------------------------------------------- #
# Sync: linearen Drift gegen die Wiedergabezeit ausgleichen
# --------------------------------------------------------------------------- #
def _run_ffprobe_json(path: str, ffprobe: str):
  """Ruft ffprobe auf und gibt das geparste JSON (dict) zurück, sonst None."""
  try:
    out = subprocess.run(
      [ffprobe, "-v", "error", "-print_format", "json",
       "-show_entries", "format=duration:stream=codec_type,duration",
       str(path)],
      capture_output=True, text=True, timeout=60,
    )
    if out.returncode != 0:
      return None
    return json.loads(out.stdout or "{}")
  except FileNotFoundError:
    print(f"[whisper] ffprobe nicht gefunden ({ffprobe}) — Sync-Korrektur deaktiviert.",
          file=sys.stderr, flush=True)
    return None
  except subprocess.TimeoutExpired:
    print("[whisper] ffprobe Timeout — Sync-Korrektur deaktiviert.", file=sys.stderr, flush=True)
    return None
  except (json.JSONDecodeError, subprocess.SubprocessError):
    return None


def _stream_duration(data: dict, codec_type: str):
  """Dauer (Sekunden) des ersten Streams mit passendem codec_type, sonst None."""
  for s in data.get("streams", []):
    if s.get("codec_type") == codec_type and s.get("duration"):
      try:
        return float(s["duration"])
      except (TypeError, ValueError):
        return None
  return None


def probe_duration(path: str, ffprobe: str, prefer: str = "video"):
  """Ermittelt die Wiedergabe-Zeitbasis der Datei via ffprobe.

  Liefert die Dauer (Sekunden), an der sich der Player orientiert: bevorzugt
  die Video-Stream-Dauer, sonst die Container-Dauer. Whisper dekodiert dagegen
  die Audiospur; weichen beide ab, driften die Untertitel linear. Gibt None
  zurück, wenn ffprobe fehlt oder keine Dauer liefert."""
  data = _run_ffprobe_json(path, ffprobe)
  if data is None:
    return None

  container = data.get("format", {}).get("duration")
  container = float(container) if container else None

  order = ["video", "audio"] if prefer == "video" else ["audio", "video"]
  for ct in order:
    d = _stream_duration(data, ct)
    if d:
      return d
  return container


def compute_time_scale(args, decoder_dur):
  """Skalierungsfaktor für die Sync-Korrektur bestimmen (oder None)."""
  if args.time_scale is not None:
    print(f"[whisper] Sync: manueller Faktor {args.time_scale:.6f}", flush=True)
    return args.time_scale
  if args.sync == "off":
    return None

  ref = args.sync_ref or args.wav
  if args.sync_ref and not os.path.exists(args.sync_ref):
    raise SystemExit(f"Sync-Referenz nicht gefunden: {args.sync_ref}")

  target = probe_duration(ref, args.ffprobe_path, prefer=args.sync)
  if args.sync_ref:
    print(f"[whisper] Sync: Referenz '{os.path.basename(ref)}'", flush=True)
  if not target:
    print("[whisper] Sync: ffprobe lieferte keine Dauer -> keine Korrektur.",
          flush=True)
    return None
  if not decoder_dur:
    print("[whisper] Sync: Decoderdauer unbekannt -> keine Korrektur.",
          flush=True)
    return None

  drift = decoder_dur - target
  if abs(drift) < 0.5:
    print(f"[whisper] Sync: Takt stimmt (Abweichung {drift:+.2f}s) "
          f"-> keine Korrektur.", flush=True)
    return None

  scale = target / decoder_dur
  print(f"[whisper] Sync: {args.sync}-Dauer {target:.1f}s vs Decoder "
        f"{decoder_dur:.1f}s (Drift {drift:+.1f}s) -> Faktor {scale:.6f}",
        flush=True)
  return scale


def apply_time_scale(cues, scale):
  if scale is None or abs(scale - 1.0) <= 1e-9:
    return cues
  return [(start * scale, end * scale, text) for start, end, text in cues]


def format_time_vtt(t):
  if t < 0:
    t = 0.0
  h = int(t // 3600)
  m = int((t % 3600) // 60)
  s = int(t % 60)
  ms = int(round((t - int(t)) * 1000))
  if ms == 1000:
    ms = 0
    s += 1
  return f"{h:02}:{m:02}:{s:02}.{ms:03}"


def _atomic_write_text(path: str, content: str) -> None:
  """Schreibt über eine tmp-Datei + os.replace(), damit ein Absturz mitten im
  Schreiben nach einem teuren GPU-Transkriptionslauf keine unvollständige,
  aber vorhandene Output-Datei hinterlässt (ein nachgelagerter Schritt könnte
  die sonst faelschlich als fertig behandeln)."""
  tmp_path = path + ".tmp"
  with open(tmp_path, "w", encoding="utf-8") as f:
    f.write(content)
  os.replace(tmp_path, path)


def load_glossary(path: str) -> str:
  if not path or not os.path.exists(path):
    return ""
  with open(path, "r", encoding="utf-8") as f:
    terms = [line.strip() for line in f if line.strip()]
  if not terms:
    return ""
  return "Wichtige Begriffe: " + ", ".join(terms)


def build_arg_parser():
  parser = argparse.ArgumentParser(description="faster-whisper CLI für CILI")
  parser.add_argument("--wav", required=True, help="Pfad zur WAV-Eingabedatei")
  parser.add_argument("--output", required=True, help="Pfad zur VTT-Ausgabedatei")
  parser.add_argument("--lang", default="auto",
                      help="Sprache (BCP-47, z.B. 'de', 'en') oder 'auto' für automatische Erkennung (default: auto)")
  parser.add_argument("--lang-output", default=None,
                      help="Pfad zu einer JSON-Datei, in die die erkannte Sprache geschrieben wird")
  parser.add_argument("--model", default="large-v3", help="Whisper-Modell (default: large-v3)")
  parser.add_argument("--device", default="cuda", help="Gerät: cuda oder cpu (default: cuda)")
  parser.add_argument("--compute-type", default="int8_float16",
                      help="Compute-Typ: int8_float16, int8, float16 etc. (default: int8_float16)")
  parser.add_argument("--beam-size", type=int, default=5,
                      help="Beam-Größe: 1=greedy/schnell, 5=Standard (default: 5)")
  parser.add_argument("--cpu-threads", type=int, default=0,
                      help="OpenMP-Threads für CPU-Inferenz (0=auto, 2-4 reduziert RAM-Peak)")
  parser.add_argument("--glossary", default=None, help="Pfad zur Glossardatei (optional)")
  parser.add_argument("--corrections", default=None, help="Pfad zur Korrekturdatei (optional)")
  # Cue-Darstellung (Tuning der Untertitel-Aufteilung)
  parser.add_argument("--max-chars", type=int, default=84,
                      help="Maximale Zeichen pro Cue, bevor ein Satz aufgeteilt wird (default: 84)")
  parser.add_argument("--max-duration", type=float, default=6.0,
                      help="Maximale Dauer pro Cue in Sekunden, bevor aufgeteilt wird (default: 6.0)")
  parser.add_argument("--max-gap", type=float, default=0.8,
                      help="Sprechpause (s), die einen neuen Cue erzwingt (default: 0.8)")
  parser.add_argument("--min-chars", type=int, default=25,
                      help="Cues unter dieser Länge (oder Einzelwörter) werden mit einem "
                           "Nachbarn verschmolzen; 0 = aus (default: 25)")
  parser.add_argument("--merge-max-chars", type=int, default=0,
                      help="Obergrenze fürs Verschmelzen kurzer Cues; 0 = automatisch "
                           "(max-chars * 1.4) (default: 0)")
  parser.add_argument("--cue-pad", type=float, default=0.2,
                      help="Nachlauf in Sekunden, der an jeden Cue angehängt wird (default: 0.2)")
  # Sync-Korrektur (linearer Drift durch Audio/Video-Clock-Mismatch)
  parser.add_argument("--sync", default="video", choices=["video", "container", "off"],
                      help="Zeitstempel linear auf die Wiedergabedauer skalieren; "
                           "bei reiner Audioquelle ein No-Op (default: video)")
  parser.add_argument("--sync-ref", default=None,
                      help="Referenzdatei (z.B. Originalvideo) für die Sync-Dauer, wenn "
                           "eine aus Video extrahierte WAV transkribiert wird")
  parser.add_argument("--time-scale", type=float, default=None,
                      help="Manueller Skalierungsfaktor; übersteuert --sync (z.B. 0.99491)")
  parser.add_argument("--ffprobe-path", default="ffprobe",
                      help="Pfad zur ffprobe-Binary (für --sync)")
  parser.add_argument("--no-speech-threshold", type=float, default=0.6,
                      help="Whisper überspringt Chunks über diesem Stille-Schwellwert (0.0–1.0, default: 0.6)")
  return parser


def main():
  args = build_arg_parser().parse_args()

  single_corrections, phrase_corrections = load_corrections(args.corrections)
  if single_corrections or phrase_corrections:
    print(f"[whisper] {len(single_corrections)} Einzelwort- und "
          f"{len(phrase_corrections)} Phrasen-Korrekturen geladen", flush=True)

  print(f"[whisper] Lade Modell '{args.model}' auf {args.device} ({args.compute_type})", flush=True)
  from faster_whisper import WhisperModel
  model = WhisperModel(
      args.model,
      device=args.device,
      compute_type=args.compute_type,
      cpu_threads=args.cpu_threads if args.cpu_threads > 0 else 0,
      num_workers=1,
  )

  initial_prompt = load_glossary(args.glossary)

  is_auto = args.lang.lower() == "auto"
  gpu_temp_suffix = f", GPU-Temp={gpu_temperature()}°C" if args.device.lower() == "cuda" else ""
  print(f"[whisper] Transkribiere: {args.wav} (beam_size={args.beam_size}, "
        f"lang={'auto-detect' if is_auto else args.lang}{gpu_temp_suffix})", flush=True)

  transcribe_kwargs = {
    "initial_prompt": initial_prompt if initial_prompt else None,
    "beam_size": args.beam_size,
    "vad_filter": True,
    "vad_parameters": {
      "threshold": 0.2,               # sehr sensitiv — fast alles gilt als Sprache
      "min_silence_duration_ms": 300, # nur sehr kurze echte Pausen werden geschnitten
      "speech_pad_ms": 800,           # großzügiger Puffer vor/nach Sprache
    },
    "temperature": 0.0,
    "compression_ratio_threshold": 1.8,
    "no_speech_threshold": args.no_speech_threshold,
    "condition_on_previous_text": False,
    "word_timestamps": True,
  }
  if not is_auto:
    transcribe_kwargs["language"] = args.lang

  segments, info = model.transcribe(args.wav, **transcribe_kwargs)

  detected_lang = info.language
  print(f"[whisper] Erkannte Sprache: {detected_lang} "
        f"(Wahrscheinlichkeit: {info.language_probability:.2f})", flush=True)
  if args.lang_output:
    _atomic_write_text(args.lang_output, json.dumps({"language": detected_lang}))

  # 1. Duplikate entfernen
  segments = remove_fuzzy_duplicates(list(segments), threshold=0.90)

  # 2. Wörter einsammeln (Zahlen-Normalisierung + Korrekturen VOR dem Cue-Bau,
  #    damit das Zeichenbudget der Cues den finalen Text widerspiegelt)
  words = flatten_words(segments)
  # Korrekturen: erst Phrasen (verschmilzt zu einem Token), dann Einzelwörter —
  # so kann eine Einzelregel kein Teilwort einer Phrase vorab "zerstören".
  words = apply_phrase_corrections_to_words(words, phrase_corrections)
  words = apply_single_corrections_to_words(words, single_corrections)

  # 3. Cues bauen: greedy aus Wort-Zeitstempeln (Satzende/Zeichen/Dauer/Pause)
  if words:
    cues = build_cues_from_words(
      words,
      max_chars=args.max_chars,
      max_duration=args.max_duration,
      max_gap=args.max_gap,
    )
  else:
    cues = cues_from_segments(segments)  # Fallback ohne Wort-Zeitstempel

  # 4. Waisen-Cues (Einzelwörter / sehr kurz) mit Nachbarn verschmelzen
  if args.min_chars > 0 and cues:
    merge_max = args.merge_max_chars or int(round(args.max_chars * 1.4))
    before = len(cues)
    cues = merge_short_cues(cues, args.min_chars, merge_max, args.max_gap)
    if before - len(cues):
      print(f"[whisper] {before - len(cues)} kurze Cues verschmolzen "
            f"(min_chars={args.min_chars}, merge_max={merge_max})", flush=True)

  # 5. Sync-Korrektur: linearen Drift gegen die Wiedergabezeit ausgleichen
  decoder_dur = getattr(info, "duration", None)
  scale = compute_time_scale(args, decoder_dur)
  cues = apply_time_scale(cues, scale)

  # 6. Kurzen Nachlauf anhängen (geclampt auf den nächsten Cue)
  cues = add_cue_padding(cues, pad=args.cue_pad)

  vtt_parts = ["WEBVTT\n\n"]
  for i, (start, end, text) in enumerate(cues, 1):
    text = " ".join(text.split())
    text = normalize_thousands(text)
    text = apply_corrections(text, phrase_corrections)
    text = apply_corrections(text, single_corrections)
    vtt_parts.append(f"{i}\n")
    vtt_parts.append(f"{format_time_vtt(start)} --> {format_time_vtt(end)}\n")
    vtt_parts.append(text + "\n\n")
  _atomic_write_text(args.output, "".join(vtt_parts))

  gpu_temp_suffix = f", GPU-Temp={gpu_temperature()}°C" if args.device.lower() == "cuda" else ""
  print(f"[whisper] Fertig: {len(cues)} Cues -> {args.output}{gpu_temp_suffix}", flush=True)

if __name__ == "__main__":
  try:
    main()
  except Exception as e:
    print(f"FEHLER: {e}", file=sys.stderr, flush=True)
    sys.exit(1)
