# Laedt ein NLLB-Modell herunter und konvertiert es ins CTranslate2-Format.
# Verwendung: python nllbdownload.py nllb-200-distilled-1.3B
import subprocess
import sys
from huggingface_hub import snapshot_download
import os

HF_ORG        = "facebook"
MODELS_DIR    = "/mnt/nllb-models"
CT2_CONVERTER = "/opt/cili/whisper-venv/bin/ct2-transformers-converter"
QUANTIZATION  = "float16"

os.environ.setdefault("HF_TOKEN", "")  # optional, extern setzen: export HF_TOKEN=hf_...
os.environ["HF_HOME"]  = f"{MODELS_DIR}/hf"

VALID_QUANTIZATIONS = {"float16", "int8_float16"}

if len(sys.argv) < 2 or len(sys.argv) > 3:
    print(f"Verwendung: python {sys.argv[0]} <modellname> [quantization]", file=sys.stderr)
    print(f"Beispiel:   python {sys.argv[0]} nllb-200-distilled-1.3B float16", file=sys.stderr)
    print(f"            python {sys.argv[0]} nllb-200-distilled-1.3B int8_float16", file=sys.stderr)
    sys.exit(1)

MODEL_NAME   = sys.argv[1]
QUANTIZATION = sys.argv[2] if len(sys.argv) == 3 else "float16"
if QUANTIZATION not in VALID_QUANTIZATIONS:
    print(f"Fehler: Ungültige Quantization '{QUANTIZATION}'. Erlaubt: {', '.join(VALID_QUANTIZATIONS)}", file=sys.stderr)
    sys.exit(1)
MODEL_DIR  = f"{MODELS_DIR}/{MODEL_NAME}"
CT2_DIR    = f"{MODELS_DIR}/{MODEL_NAME}-ct2"

print(f"Lade {HF_ORG}/{MODEL_NAME} herunter …")
snapshot_download(
    repo_id=f"{HF_ORG}/{MODEL_NAME}",
    local_dir=MODEL_DIR,
)
print("Download abgeschlossen.")

print(f"Konvertiere nach CTranslate2 ({QUANTIZATION}) → {CT2_DIR} …")
result = subprocess.run(
    [CT2_CONVERTER,
     "--model",        MODEL_DIR,
     "--output_dir",   CT2_DIR,
     "--quantization", QUANTIZATION],
    check=False,
)
if result.returncode != 0:
    print(f"Fehler: Konvertierung fehlgeschlagen (Exit-Code {result.returncode})", file=sys.stderr)
    sys.exit(result.returncode)

print(f"Konvertierung abgeschlossen: {CT2_DIR}")
