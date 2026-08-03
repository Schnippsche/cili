#!/usr/bin/env python3
"""
NLLB-200 Offline-Übersetzer für VTT-Untertitel.

Aufruf:
    python3 translate_worker.py \
        --input  /tmp/in.vtt   --output /tmp/out.vtt \
        --source de             --target pl \
        --model  /opt/cili/nllb/nllb-600M \
        --device cpu            --compute-type int8 \
        --beam-size 4

Exit 0 bei Erfolg, 1 bei jedem Fehler (Details auf stderr).
"""

import argparse
import os
import subprocess
import sys
from dataclasses import dataclass
from typing import List


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

# Windows: DLL-Verzeichnisse der nvidia-pip-Pakete beim Modul-Laden registrieren,
# damit ctranslate2 cublas64_12.dll usw. findet, wenn ein CUDA-Gerät initialisiert
# wird. Muss VOR jedem ctranslate2-Import passieren; sowohl PATH als auch
# add_dll_directory werden gesetzt, um mit verschiedenen LoadLibrary-Aufrufstellen
# maximal kompatibel zu sein.
if sys.platform == "win32":
    import glob as _glob
    import site as _site_mod
    for _site in _site_mod.getsitepackages():
        _nvidia = os.path.join(_site, "nvidia")
        if not os.path.isdir(_nvidia):
            continue
        for _sub in os.listdir(_nvidia):
            _bin = os.path.join(_nvidia, _sub, "bin")
            if os.path.isdir(_bin):
                os.environ["PATH"] = _bin + os.pathsep + os.environ.get("PATH", "")
                try:
                    os.add_dll_directory(_bin)
                except OSError:
                    pass
    for _cuda_bin in _glob.glob(
        r"C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v*\bin"
    ):
        os.environ["PATH"] = _cuda_bin + os.pathsep + os.environ.get("PATH", "")
        try:
            os.add_dll_directory(_cuda_bin)
        except OSError:
            pass


# ── Sprach-Zuordnung ─────────────────────────────────────────────────────────
# ISO-639-1/3 → NLLB-200 Sprache+Schrift-Codes. Muss eine Obermenge jedes Codes
# sein, den das Backend anfordern kann (language_options.translation_supported = 1).
NLLB_CODES = {
    # West/Central European
    "de":  "deu_Latn",
    "en":  "eng_Latn",
    "fr":  "fra_Latn",
    "es":  "spa_Latn",
    "it":  "ita_Latn",
    "pt":  "por_Latn",
    "nl":  "nld_Latn",
    "pl":  "pol_Latn",
    "cs":  "ces_Latn",
    "sk":  "slk_Latn",
    "hu":  "hun_Latn",
    "ro":  "ron_Latn",
    "sv":  "swe_Latn",
    "da":  "dan_Latn",
    "fi":  "fin_Latn",
    "nb":  "nob_Latn",
    "is":  "isl_Latn",
    "fo":  "fao_Latn",
    "lb":  "ltz_Latn",
    "ca":  "cat_Latn",
    "gl":  "glg_Latn",
    "eu":  "eus_Latn",
    "oc":  "oci_Latn",
    "ast": "ast_Latn",
    "scn": "scn_Latn",
    "lmo": "lmo_Latn",
    # South/Southeast European
    "el":  "ell_Grek",
    "tr":  "tur_Latn",
    "sq":  "sqi_Latn",
    "hr":  "hrv_Latn",
    "bs":  "bos_Latn",
    "sl":  "slv_Latn",
    "mk":  "mkd_Cyrl",
    "mt":  "mlt_Latn",
    # Celtic / Insular
    "cy":  "cym_Latn",
    "ga":  "gle_Latn",
    "gd":  "gla_Latn",
    "br":  "bre_Latn",
    # East European / Slavic (Cyrillic)
    "ru":  "rus_Cyrl",
    "uk":  "ukr_Cyrl",
    "be":  "bel_Cyrl",
    "bg":  "bul_Cyrl",
    "sr":  "srp_Cyrl",
    # Baltic
    "lt":  "lit_Latn",
    "lv":  "lvs_Latn",
    "et":  "est_Latn",
    # Caucasian
    "ka":  "kat_Geor",
    "hy":  "hye_Armn",
    "az":  "azj_Latn",
    # Other
    "yi":  "ydd_Hebr",
    # Asian
    "ja":  "jpn_Jpan",
    "zh":  "zho_Hans",
}


# ── VTT-Parsing ───────────────────────────────────────────────────────────────

@dataclass
class Cue:
    cue_id: str           # z.B. "1" oder "" falls nicht vorhanden
    timestamps: str       # z.B. "00:00:01.000 --> 00:00:03.000"
    text: str             # zusammengefügter mehrzeiliger Text (einzeilig)


def _parse_cue_block(lines: List[str]) -> Cue | None:
    """Baut aus den (nicht-leeren) Zeilen eines Blocks einen Cue.

    Block-Layout: optionale Kennungszeile (ohne "-->"), dann Zeitstempelzeile,
    dann beliebig viele Textzeilen. Liefert None, wenn keine gültige
    Zeitstempelzeile vorhanden ist (z.B. NOTE-Block)."""
    idx = 0
    cue_id = ""
    if "-->" not in lines[idx]:
        cue_id = lines[idx].strip()
        idx += 1
    if idx >= len(lines) or "-->" not in lines[idx]:
        return None
    timestamps = lines[idx].strip()
    text = " ".join(l.strip() for l in lines[idx + 1:])
    return Cue(cue_id=cue_id, timestamps=timestamps, text=text)


def parse_vtt(content: str) -> List[Cue]:
    """
    Parst einen VTT-String in eine Liste von Cue-Objekten.
    Mehrzeilige Cue-Texte werden mit einem Leerzeichen zu einer Zeile verbunden.

    Cues sind durch Leerzeilen getrennt; jeder Block wird einzeln geparst. Eine
    führende WEBVTT-Kopfzeile (auch innerhalb des ersten Blocks) wird verworfen.
    """
    cues: List[Cue] = []
    block: List[str] = []

    def consume(lines: List[str]) -> None:
        if lines and lines[0].startswith("WEBVTT"):
            lines = lines[1:]   # Kopfzeile verwerfen, Rest des Blocks behalten
        if not lines:
            return
        cue = _parse_cue_block(lines)
        if cue:
            cues.append(cue)

    for line in content.splitlines():
        if line.strip():
            block.append(line)
        elif block:           # Leerzeile beendet einen Block
            consume(block)
            block = []
    if block:                 # letzter Block ohne abschließende Leerzeile
        consume(block)

    return cues


def write_vtt(cues: List[Cue], translated_texts: List[str]) -> str:
    """
    Schreibt eine neue VTT-Datei mit den originalen Zeitstempeln und den übersetzten
    Texten. Jeder Cue erhält eine automatisch hochgezählte numerische ID.
    """
    if len(translated_texts) != len(cues):
        raise ValueError(
            f"Anzahl stimmt nicht überein: {len(cues)} Cues, aber {len(translated_texts)} Übersetzungen"
        )
    parts = ["WEBVTT", ""]
    for idx, (cue, text) in enumerate(zip(cues, translated_texts), start=1):
        parts.append(str(idx))
        parts.append(cue.timestamps)
        parts.append(text)
        parts.append("")
    return "\n".join(parts)


# ── Übersetzung ────────────────────────────────────────────────────────────────

def load_model(model_path: str, device: str, compute_type: str):
    """
    Lädt (sp, translator) einmalig und gibt sie zurück — Wiederverwendung über alle
    Batches hinweg. Der Import hier hält die Abhängigkeit optional und liefert eine
    klare Fehlermeldung, falls sie fehlt.
    """
    try:
        import ctranslate2
        import sentencepiece as spm
    except ImportError as exc:
        print(f"Fehlende Abhängigkeit: {exc}", file=sys.stderr)
        print("Installieren: pip install ctranslate2>=3.20 sentencepiece>=0.1.99", file=sys.stderr)
        raise

    sp = spm.SentencePieceProcessor(
        model_file=os.path.join(model_path, "sentencepiece.bpe.model")
    )
    translator = ctranslate2.Translator(model_path, device=device, compute_type=compute_type)
    return sp, translator


def translate_batch(
    texts: List[str],
    source_lang: str,
    target_lang: str,
    sp,
    translator,
    beam_size: int,
    max_batch_size: int = 0,
) -> List[str]:
    """Übersetzt einen Batch Texte mit einem vorab geladenen (sp, translator)-Paar.

    max_batch_size > 0 lässt CTranslate2 die übergebenen Sequenzen intern nach
    Token-Anzahl in Teilbatches packen (bessere GPU-Auslastung); 0 = unbegrenzt.
    """
    src_code = NLLB_CODES[source_lang]
    tgt_code = NLLB_CODES[target_lang]

    # Tokenisieren — NLLB-Format: pieces + </s> + src_lang
    source_tokens: List[List[str]] = []
    for text in texts:
        pieces = sp.encode(text, out_type=str)
        source_tokens.append(pieces + ['</s>', src_code])

    results = translator.translate_batch(
        source_tokens,
        target_prefix=[[tgt_code]] * len(source_tokens),
        beam_size=beam_size,
        max_batch_size=max_batch_size,
    )

    translated: List[str] = []
    for res in results:
        hypothesis = res.hypotheses[0]
        # Führendes Ziel-Sprachtoken und Sondertokens entfernen
        output_pieces = [t for t in hypothesis if t not in (tgt_code, '</s>', '<pad>')]
        translated.append(sp.decode(output_pieces))

    return translated


# ── Einstiegspunkt ─────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="NLLB-200 VTT-Untertitel-Übersetzer")
    parser.add_argument("--input",        required=True, help="Pfad der Eingabe-VTT-Datei")
    parser.add_argument("--output",       required=True, help="Pfad der Ausgabe-VTT-Datei")
    parser.add_argument("--source",       required=True, help="Quell-Sprachcode (ISO-639-1)")
    parser.add_argument("--target",       required=True, help="Ziel-Sprachcode (ISO-639-1)")
    parser.add_argument("--model",        required=True, help="CTranslate2-NLLB-Modellverzeichnis")
    parser.add_argument("--device",       default="cpu",  help="cpu | cuda")
    parser.add_argument("--compute-type", default="int8", help="int8 | int8_float16 | float32")
    parser.add_argument("--beam-size",    type=int, default=4)
    parser.add_argument("--batch-size",   type=int, default=32,
                        help="Anzahl Cues pro translate_batch-Aufruf (begrenzt den Spitzen-RAM)")
    parser.add_argument("--max-batch-size", type=int, default=0, dest="max_batch_size",
                        help="Interne CTranslate2-Teilbatch-Größe nach Tokens (0 = unbegrenzt)")
    args = parser.parse_args()

    # Quell- und Zielsprache gegen die Sprachtabelle prüfen
    if args.source not in NLLB_CODES:
        print(f"Nicht unterstützte Quellsprache: {args.source}. "
              f"Unterstützt: {sorted(NLLB_CODES.keys())}", file=sys.stderr)
        sys.exit(1)
    if args.target not in NLLB_CODES:
        print(f"Nicht unterstützte Zielsprache: {args.target}. "
              f"Unterstützt: {sorted(NLLB_CODES.keys())}", file=sys.stderr)
        sys.exit(1)

    if not os.path.isfile(args.input):
        print(f"FEHLER: Eingabedatei nicht gefunden: {args.input}", file=sys.stderr)
        sys.exit(1)

    with open(args.input, encoding="utf-8") as fh:
        vtt_content = fh.read()

    cues = parse_vtt(vtt_content)
    if not cues:
        print("Keine Cues in der Eingabe-VTT-Datei gefunden.", file=sys.stderr)
        sys.exit(1)

    gpu_temp_suffix = f", GPU-Temp={gpu_temperature()}°C" if args.device.lower() == "cuda" else ""
    print(f"Übersetze {len(cues)} Cues von {args.source} → {args.target} "
          f"(batch_size={args.batch_size}{gpu_temp_suffix}) …", flush=True)

    # Modell nur einmal laden und über alle Batches wiederverwenden
    try:
        print(f"Lade Modell aus {args.model} …", flush=True)
        sp, translator = load_model(args.model, args.device, args.compute_type)
    except Exception as exc:
        print(f"FEHLER: Modell konnte nicht geladen werden: {exc}", file=sys.stderr)
        sys.exit(1)

    all_texts = [c.text for c in cues]
    translated_texts: List[str] = []
    try:
        # In Batches der Größe batch-size übersetzen (begrenzt den Spitzen-RAM)
        for start in range(0, len(all_texts), args.batch_size):
            chunk = all_texts[start:start + args.batch_size]
            end = min(start + args.batch_size, len(all_texts))
            print(f"  Cues {start + 1}–{end} / {len(all_texts)} …", flush=True)
            translated_texts.extend(translate_batch(
                chunk,
                source_lang=args.source,
                target_lang=args.target,
                sp=sp,
                translator=translator,
                beam_size=args.beam_size,
                max_batch_size=args.max_batch_size,
            ))
    except Exception as exc:
        print(f"Übersetzung fehlgeschlagen: {exc}", file=sys.stderr)
        sys.exit(1)

    output_vtt = write_vtt(cues, translated_texts)
    # Atomar schreiben (tmp-Datei + os.replace): nach einem potenziell langen
    # Batch-Übersetzungslauf soll ein Absturz mitten im Schreiben nicht eine
    # unvollständige, aber vorhandene Output-Datei hinterlassen.
    tmp_output = args.output + ".tmp"
    with open(tmp_output, "w", encoding="utf-8") as fh:
        fh.write(output_vtt)
    os.replace(tmp_output, args.output)

    gpu_temp_suffix = f" (GPU-Temp={gpu_temperature()}°C)" if args.device.lower() == "cuda" else ""
    print(f"Fertig. {len(cues)} übersetzte Cues nach {args.output} geschrieben.{gpu_temp_suffix}")


if __name__ == "__main__":
    main()
