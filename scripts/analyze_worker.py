#!/usr/bin/env python3
"""
Sendet Untertiteltext an Ollama und gibt die Antwort auf stdout aus.
Aufruf: python analyze_worker.py --text-file <path> --prompt-file <path>
        [--model qwen2.5:7b] [--url http://localhost:11434] [--timeout 300]
"""
import argparse
import math
import os
import random
import re
import subprocess
import sys
import time
import requests

# Retry-Konfiguration für den Ollama-Aufruf: ohne Retry bricht ein einzelner
# transienter Hänger (Modell lädt gerade, kurzer OOM, 502 vom Reverse-Proxy)
# den kompletten Analyse-Lauf ab, statt sich selbst zu erholen.
OLLAMA_MAX_RETRIES = 3
OLLAMA_BASE_BACKOFF = 2.0
OLLAMA_RETRYABLE_STATUS_CODES = frozenset({429, 500, 502, 503, 504})

_SUBTITLE_INDEX_RE = re.compile(r'\d+')
_SUBTITLE_TIMESTAMP_RE = re.compile(r'[\d:]+[\d.,]+ -->')


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


def strip_subtitle_formatting(text: str) -> str:
    """Entfernt VTT/SRT-Timestamps und Header, behält nur Textzeilen."""
    lines = text.splitlines()
    result = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        if line == 'WEBVTT':
            continue
        if _SUBTITLE_INDEX_RE.fullmatch(line):
            continue
        if _SUBTITLE_TIMESTAMP_RE.match(line):
            continue
        result.append(line)
    return ' '.join(result)


def fit_text_to_budget(text: str, max_chars: int) -> str:
    """Kürzt Text auf max_chars, indem gleichmäßig über Anfang, Mitte und Ende gesampelt wird."""
    if len(text) <= max_chars:
        return text
    # Je ein Drittel vom Anfang, der Mitte und dem Ende
    third = max_chars // 3
    mid_start = (len(text) - third) // 2
    result = (
        text[:third]
        + ' [...] '
        + text[mid_start:mid_start + third]
        + ' [...] '
        + text[-third:]
    )
    print(f"[analyze] Text von {len(text)} auf {len(result)} Zeichen gekürzt (Anfang/Mitte/Ende)", file=sys.stderr)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--text-file',   required=True, help='Pfad zur Datei mit Untertitelinhalt')
    parser.add_argument('--prompt-file', required=True, help='Pfad zur Prompt-Datei')
    parser.add_argument('--model',       default='qwen2.5:7b')
    parser.add_argument('--url',         default='http://localhost:11434')
    parser.add_argument('--timeout',     type=int, default=300)
    parser.add_argument('--num-ctx',     type=int, default=32768, help='Kontextfenster in Token')
    args = parser.parse_args()

    for path, label in [(args.text_file, '--text-file'), (args.prompt_file, '--prompt-file')]:
        if not os.path.isfile(path):
            print(f"ERROR: {label} not found: {path}", file=sys.stderr)
            sys.exit(1)

    with open(args.text_file, 'r', encoding='utf-8') as f:
        raw_text = f.read()

    with open(args.prompt_file, 'r', encoding='utf-8') as f:
        prompt_template = f.read().strip()

    plain_text = strip_subtitle_formatting(raw_text)
    if not plain_text:
        print(f"[analyze] Kein Text nach Formatbereinigung — leere Antwort.", file=sys.stderr)
        print('', end='')
        sys.exit(0)

    # Das Ausgabeformat (7 Abschnitte + ggf. Produktdetails) ist umfangreich, daher
    # ein großzügiges Token-Budget für die Antwort reservieren. Dieselbe Zahl wird
    # sowohl für das Eingabe-Textbudget als auch für die Kontextgröße verwendet,
    # damit beide Berechnungen konsistent bleiben.
    output_budget_tokens = 8192

    # Maximales Textbudget: num_ctx - Prompt-Overhead - Output-Buffer (je ~4 Zeichen/Token)
    prompt_overhead_chars = len(prompt_template) + 100
    output_buffer_chars   = output_budget_tokens * 4
    max_text_chars = (args.num_ctx * 4) - prompt_overhead_chars - output_buffer_chars
    plain_text = fit_text_to_budget(plain_text, max_text_chars)

    full_prompt = f"{prompt_template}\n\n{plain_text}"

    # Kontextgröße dynamisch anpassen: ~4 Zeichen/Token + Output-Buffer
    estimated_tokens = math.ceil(len(full_prompt) / 4)
    actual_ctx = min(args.num_ctx, max(2048, estimated_tokens + output_budget_tokens))

    print(f"[analyze] Sende {len(plain_text)} Zeichen an {args.url} (Modell: {args.model}, ctx={actual_ctx}/{args.num_ctx}, GPU-Temp={gpu_temperature()}°C)", file=sys.stderr)

    def call_ollama(prompt: str, context):
        payload = {
            "model": args.model,
            "prompt": prompt,
            "stream": False,
            # Qwen3 u.ä. Reasoning-Modelle verbrauchen sonst einen Großteil des
            # Kontextfensters für <think>-Tokens, wodurch die eigentliche Antwort
            # abgeschnitten werden kann. Ohne Denkschritte steht das volle
            # Ausgabebudget für die strukturierte Zusammenfassung zur Verfügung.
            "think": False,
            "options": {
                "num_ctx": actual_ctx,
                "num_predict": -1,   # unbegrenzte Ausgabelänge
                # Ollamas Standard (repeat_last_n=64 Token) schaut nicht weit genug zurück,
                # um das Wiederholen ganzer Absätze/Kapitel (>100 Token) zu erkennen — das
                # Modell kann sich sonst in einer Schleife festfahren und denselben
                # Abschnitt endlos wiederholen, bis das Kontextlimit erreicht ist.
                # Wichtig: NICHT -1 (kompletter Kontext) verwenden — das bestraft auch
                # ganz normale, zwangsläufig wiederkehrende Wörter (der/die/und/ist) über
                # eine lange Antwort hinweg und drängt das Modell in fremdsprachige/
                # inkohärente Ausweichformulierungen. 512 Token deckt mehrere Absätze ab,
                # ohne den gesamten bisherigen Text als "schon benutzt" zu bestrafen.
                "repeat_last_n": 512,
                "repeat_penalty": 1.15,
            },
        }
        if context is not None:
            payload["context"] = context

        for attempt in range(OLLAMA_MAX_RETRIES):
            is_last = attempt == OLLAMA_MAX_RETRIES - 1
            backoff = (OLLAMA_BASE_BACKOFF * (2 ** attempt)) + random.uniform(0, OLLAMA_BASE_BACKOFF)
            try:
                resp = requests.post(f"{args.url}/api/generate", json=payload, timeout=args.timeout)
                if not is_last and resp.status_code in OLLAMA_RETRYABLE_STATUS_CODES:
                    retry_after = resp.headers.get("Retry-After")
                    wait = float(retry_after) if retry_after else backoff
                    print(f"[analyze] Ollama-Fehler ({resp.status_code}), Retry {attempt + 1}/"
                          f"{OLLAMA_MAX_RETRIES - 1} in {wait:.1f}s …", file=sys.stderr)
                    time.sleep(wait)
                    continue
                resp.raise_for_status()
                return resp.json()
            except requests.exceptions.Timeout:
                if is_last:
                    print(f"ERROR: Ollama request timed out after {args.timeout}s", file=sys.stderr)
                    sys.exit(1)
                print(f"[analyze] Ollama-Timeout, Retry {attempt + 1}/{OLLAMA_MAX_RETRIES - 1} "
                      f"in {backoff:.1f}s …", file=sys.stderr)
                time.sleep(backoff)
            except requests.exceptions.ConnectionError as e:
                if is_last:
                    print(f"ERROR: cannot connect to Ollama at {args.url}: {e}", file=sys.stderr)
                    sys.exit(1)
                print(f"[analyze] Ollama-Verbindungsfehler, Retry {attempt + 1}/{OLLAMA_MAX_RETRIES - 1} "
                      f"in {backoff:.1f}s …", file=sys.stderr)
                time.sleep(backoff)
            except requests.exceptions.HTTPError as e:
                print(f"ERROR: Ollama returned {e.response.status_code}: {e.response.text[:300]}",
                      file=sys.stderr)
                sys.exit(1)

    # Bricht Ollama wegen des Kontextlimits ab (done_reason=length), wird die
    # Generierung anhand des zurückgegebenen Token-Kontexts nahtlos fortgesetzt,
    # bis die Antwort natürlich endet. MAX_CONTINUATIONS begrenzt das als
    # Sicherheitsnetz gegen endlose Fortsetzungen bei extrem langen Eingaben.
    MAX_CONTINUATIONS = 5
    result_parts = []
    context = None
    prompt_for_call = full_prompt
    done_reason = None

    for attempt in range(MAX_CONTINUATIONS + 1):
        body = call_ollama(prompt_for_call, context)
        chunk = body.get('response', '')
        result_parts.append(chunk)
        done_reason = body.get('done_reason')
        if done_reason != 'length':
            break
        print(f"[analyze] Antwort durch Kontextlimit abgeschnitten — setze fort "
              f"(Fortsetzung {attempt + 1}/{MAX_CONTINUATIONS}, bisher {len(''.join(result_parts))} Zeichen)",
              file=sys.stderr)
        context = body.get('context')
        # Ein wirklich leerer Prompt wird von Ollama als reiner "Modell laden"-Request
        # behandelt (done_reason=load, kein generierter Text) — ein einzelnes Leerzeichen
        # ist nicht leer und setzt die Generierung anhand von `context` nahtlos fort.
        prompt_for_call = ' '
    else:
        print(f"WARNUNG: Maximale Anzahl Fortsetzungen ({MAX_CONTINUATIONS}) erreicht — "
              f"Antwort ist möglicherweise weiterhin unvollständig", file=sys.stderr)

    result = ''.join(result_parts)
    print(f"[analyze] Antwort abgeschlossen (done_reason={done_reason}, {len(result)} Zeichen, GPU-Temp={gpu_temperature()}°C)", file=sys.stderr)
    print(result, end='')


if __name__ == '__main__':
    main()
