import os

os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")

from faster_whisper import WhisperModel
import re
import shutil
import time
from difflib import SequenceMatcher

# -----------------------------------------
# Korrekturen für häufige Transkriptionsfehler
# -----------------------------------------
CORRECTIONS = [
  (r'\bSilly\b', 'Cili'),
  (r'\bSili\b', 'Cili'),
  (r'\bSilli\b', 'Cili'),
  (r'\bSealy\b', 'Cili'),
  (r'\bZili\b', 'Cili'),
  (r'\bZilli\b', 'Cili'),
  (r'\bGrake\b', 'Graack'),
  (r'\bGraag\b', 'Graack'),
  (r'\bLattke\b', 'Hlatky'),
  (r'\bCili bei Design\b', 'Cili by Design'),
  (r'\bBrighton\b', 'Brad und'),
  (r'\bwilbersäule\b', 'Wirbelsäule'),
  (r'\bEczem\b', 'Ekzem'),
  (r'\bSwisch\b', 'Swish'),
  (r'\bZwisch\b', 'Swish'),
]


def apply_corrections(text: str) -> str:
  for pattern, replacement in CORRECTIONS:
    text = re.sub(pattern, replacement, text, flags=re.IGNORECASE)
  return text


# -----------------------------------------
# Fuzzy-Duplikatfilter
# -----------------------------------------
def similar(a, b):
  return SequenceMatcher(None, a, b).ratio()


def remove_fuzzy_duplicates(segments, threshold=0.85):
  cleaned = []
  last_text = ""

  for seg in segments:
    text = seg.text.strip()
    if similar(text, last_text) >= threshold:
      continue
    cleaned.append(seg)
    last_text = text

  return cleaned


def merge_short_cues(cues, min_duration=2.0):
  """Fusioniert zu kurze Cues mit dem vorherigen."""
  if not cues:
    return cues
  result = [cues[0]]
  for start, end, text in cues[1:]:
    if end - start < min_duration:
      prev_start, prev_end, prev_text = result[-1]
      result[-1] = (prev_start, end, prev_text + ' ' + text)
    else:
      result.append((start, end, text))
  return result


def build_cues(segments, max_duration=6.0, max_chars=80):
  """Teilt Segmente anhand von Word-Timestamps an Satzenden oder bei Überlänge."""
  cues = []
  buf_words = []
  buf_start = None
  buf_end = None

  def flush():
    nonlocal buf_words, buf_start, buf_end
    if buf_words and buf_start is not None:
      cues.append((buf_start, buf_end, ''.join(buf_words).strip()))
    buf_words, buf_start, buf_end = [], None, None

  for seg in segments:
    words = seg.words or []
    if not words:
      flush()
      if seg.text.strip():
        cues.append((seg.start, seg.end, seg.text.strip()))
      continue

    for w in words:
      if buf_start is None:
        buf_start = w.start
      buf_words.append(w.word)
      buf_end = w.end
      text = ''.join(buf_words).strip()
      duration = w.end - buf_start
      sentence_end = w.word.rstrip().endswith(('.', '!', '?'))
      comma = w.word.rstrip().endswith((',', ';'))
      # Komma-Split ab halber Schwelle, damit frühe Kommas nicht übersprungen werden
      split_at_comma = comma and (
            duration >= max_duration * 0.5 or len(text) >= max_chars // 2)

      if sentence_end or split_at_comma or duration >= max_duration:
        flush()

  flush()
  return cues


# -----------------------------------------
# Pfade
# -----------------------------------------
INPUT_DIR = "input"
OUTPUT_DIR = "output"
DONE_DIR = "done"
GLOSSARY_FILE = "glossar.txt"

# -----------------------------------------
# Whisper-Modell
# -----------------------------------------
model = WhisperModel(
  "large-v3",
  device="cuda",
  compute_type="int8_float16",
)


# -----------------------------------------
# Glossar laden
# -----------------------------------------
def load_glossary(path: str) -> str:
  if not os.path.exists(path):
    return ""
  with open(path, "r", encoding="utf-8") as f:
    terms = [line.strip() for line in f if line.strip()]
  if not terms:
    return ""
  return "Wichtige Begriffe: " + ", ".join(terms)


# -----------------------------------------
# Zeitformat für VTT
# -----------------------------------------
def format_time_vtt(t):
  h = int(t // 3600)
  m = int((t % 3600) // 60)
  s = int(t % 60)
  ms = int((t - int(t)) * 1000)
  return f"{h:02}:{m:02}:{s:02}.{ms:03}"


# -----------------------------------------
# Ordner vorbereiten
# -----------------------------------------
os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(DONE_DIR, exist_ok=True)

initial_prompt = load_glossary(GLOSSARY_FILE)

input_files = [f for f in os.listdir(INPUT_DIR) if
               os.path.isfile(os.path.join(INPUT_DIR, f))]

# -----------------------------------------
# Verarbeitung
# -----------------------------------------
if not input_files:
  print("Keine Dateien im input-Ordner gefunden.")
else:
  for filename in input_files:
    input_path = os.path.join(INPUT_DIR, filename)
    stem = os.path.splitext(filename)[0]
    vtt_path = os.path.join(OUTPUT_DIR, stem + ".vtt")
    done_path = os.path.join(DONE_DIR, filename)

    print(f"Verarbeite: {filename}")
    start_time = time.time()

    try:
      segments, info = model.transcribe(
        input_path,
        language="de",
        initial_prompt=initial_prompt if initial_prompt else None,
        beam_size=5,
        vad_filter=True,
        vad_parameters={
          "threshold": 0.2,               # sehr sensitiv — fast alles gilt als Sprache
          "min_silence_duration_ms": 300, # nur sehr kurze echte Pausen werden geschnitten
          "speech_pad_ms": 800,           # großzügiger Puffer vor/nach Sprache
        },
        temperature=0.0,
        compression_ratio_threshold=1.8,
        no_speech_threshold=0.1,          # Whisper überspringt Chunks nur bei >90% Stille-Sicherheit
        condition_on_previous_text=False,
        word_timestamps=True,
      )

      segments = remove_fuzzy_duplicates(segments)

      cues = merge_short_cues(build_cues(segments))
      with open(vtt_path, "w", encoding="utf-8") as vtt_file:
        vtt_file.write("WEBVTT\n\n")
        for i, (start, end, text) in enumerate(cues, 1):
          text = apply_corrections(text)
          vtt_file.write(f"{i}\n")
          vtt_file.write(
            f"{format_time_vtt(start)} --> {format_time_vtt(end)}\n")
          vtt_file.write(text + "\n\n")

      shutil.move(input_path, done_path)

      elapsed = time.time() - start_time
      minutes = int(elapsed // 60)
      seconds = int(elapsed % 60)
      print(f"Fertig: {vtt_path} | Dauer: {minutes:02}:{seconds:02}")

    except Exception as e:
      print(f"FEHLER bei {filename}: {e}")
