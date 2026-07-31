#!/usr/bin/env python3
"""
Telegram → CILI Erfahrungsberichte Importer

Einrichtung:
  1. pip install telethon requests python-dotenv
  2. Telegram API-Zugangsdaten holen: https://my.telegram.org/apps
  3. .env-Datei anlegen (Vorlage: scripts/telegram_import.env.example) —
     TG_SOURCE muss exakt "Mensch" oder "Tier" sein.
  4. Beim ersten Start: Telefonnummer + Code eingeben (Session wird danach gespeichert)

Verwendung:
  python scripts/telegram_import.py
      Importiert alle Nachrichten, die neuer sind als der aktuellste CILI-Eintrag.

  python scripts/telegram_import.py --from-date 2026-06-01
      Nützlich beim allerersten Import: Startdatum manuell setzen (CILI noch leer).

  python scripts/telegram_import.py --env scripts/telegram_import.env
      Explizite .env-Datei angeben (Standard: .env im aktuellen Verzeichnis).
"""

import argparse
import asyncio
import json
import os
import random
import re
import subprocess
import sys
import time
from collections import defaultdict
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path

import requests
import ftfy
from dotenv import load_dotenv
from telethon import TelegramClient

sys.path.insert(0, str(Path(__file__).parent))
from video_upload import download_and_upload as _download_and_upload_video

def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Telegram → CILI Erfahrungsberichte Importer")
    parser.add_argument("--from-date", metavar="YYYY-MM-DD",
                        help="Startdatum für den Import (Standard: letzter CILI-Eintrag)")
    parser.add_argument("--env", metavar="PFAD",
                        help="Pfad zur .env-Datei (Standard: .env im Arbeitsverzeichnis)")
    return parser.parse_args()

_args = _parse_args()
if _args.env:
    _env_file = Path(_args.env)
    if not _env_file.exists():
        sys.exit(f"Fehler: .env-Datei nicht gefunden: {_env_file}")
    load_dotenv(_env_file)
else:
    load_dotenv()

# ── Konfiguration ──────────────────────────────────────────────────────────────

TG_API_ID   = int(os.getenv("TG_API_ID", "0"))
TG_API_HASH = os.getenv("TG_API_HASH", "")
TG_PHONE    = os.getenv("TG_PHONE", "")    # z.B. +4917012345678
TG_GROUP    = os.getenv("TG_GROUP", "")    # Gruppenname, -link oder numerische ID

# Basis-URL von Ollama (ohne Pfad) — classify_with_ai() spricht die native /api/generate-Route
# an, nicht die OpenAI-kompatible /v1/chat/completions (dort unterdrückt think:false das
# Qwen3-Reasoning nicht zuverlässig, siehe Kommentar in classify_with_ai). Damit funktioniert
# nur noch echtes Ollama als Backend, kein LM Studio o.ä. mehr.
AI_URL      = os.getenv("AI_URL", "http://localhost:11434")
AI_MODEL    = os.getenv("AI_MODEL", "llama3")
AI_TIMEOUT  = int(os.getenv("AI_TIMEOUT", "180"))
# Kontextfenster und Antwortlänge für die Klassifikation. Da think:false über die native Route
# zuverlässig greift, muss hier kein Reasoning-Overhead mehr eingeplant werden — die Antwort ist
# ein kurzes JSON-Objekt (siehe CLASSIFY_PROMPT).
AI_NUM_CTX     = int(os.getenv("AI_NUM_CTX", "4096"))
AI_NUM_PREDICT = int(os.getenv("AI_NUM_PREDICT", "1024"))
# Wartezeit vor dem ersten Retry nach einem Ollama-ReadTimeout (verdoppelt sich je Versuch).
# Höher als der Standard-HTTP-Retry: ein neu ladendes Modell braucht eher zehn(e) Sekunden als eine.
AI_RETRY_BACKOFF_SECONDS = float(os.getenv("AI_RETRY_BACKOFF_SECONDS", "15"))

CILI_URL    = os.getenv("CILI_URL", "http://localhost:8080")
CILI_USER   = os.getenv("CILI_USER", "admin")
CILI_PASS   = os.getenv("CILI_PASS", "admin")

MIN_CONFIDENCE = float(os.getenv("MIN_CONFIDENCE", "0.75"))

# Begrenzung pro Lauf: bei großen Backlogs (z.B. Erstimport einer Gruppe mit 700+ Nachrichten)
# nicht alles in einem Rutsch verarbeiten, sondern über mehrere Läufe strecken. Welches Limit
# zuerst greift, beendet den Lauf — der Rest folgt beim nächsten Mal (State wird pro Nachricht
# gespeichert, siehe _save_state_date-Aufruf in der Hauptschleife).
MAX_MESSAGES_PER_RUN = int(os.getenv("MAX_MESSAGES_PER_RUN", "50"))
MAX_RUNTIME_MINUTES  = float(os.getenv("MAX_RUNTIME_MINUTES", "45"))

WEBINAR_FOLDER_ID  = os.getenv("WEBINAR_FOLDER_ID", "")
WEBINAR_MAX_HEIGHT = int(os.getenv("WEBINAR_MAX_HEIGHT", "720"))

TG_SOURCE   = os.getenv("TG_SOURCE", "")   # muss exakt "Mensch" oder "Tier" sein
TG_IS_HUMAN = TG_SOURCE == "Mensch"
TG_IS_ANIMAL = TG_SOURCE == "Tier"

# Bewusst NICHT pro Quelle aufgeteilt — alle Telegram-Quellen nutzen denselben Account
# und damit dieselbe Telethon-Session. Zwei Prozesse dürfen diese Datei nie gleichzeitig
# öffnen (SQLite "database is locked"), daher serialisiert AsyncConfig.telegramExecutor()
# (maxPoolSize=1) alle Importe java-seitig auf einen Prozess zur selben Zeit.
SESSION_FILE = str(Path(__file__).parent / "telegram_session")
_STATE_FILE_SUFFIX = re.sub(r"[^a-zA-Z0-9_-]", "_", TG_SOURCE) if TG_SOURCE else "default"
_STATE_FILE = Path(__file__).parent / f"telegram_import_{_STATE_FILE_SUFFIX}.state"


def _load_state_date() -> datetime | None:
    """Letzter erfolgreich verarbeiteter Telegram-Zeitstempel (UTC, naiv)."""
    try:
        return datetime.fromisoformat(_STATE_FILE.read_text().strip())
    except (FileNotFoundError, ValueError):
        return None


def _save_state_date(dt: datetime) -> None:
    _STATE_FILE.write_text(dt.isoformat())

# ── KI-Prompt ─────────────────────────────────────────────────────────────────

CLASSIFY_PROMPT = """\
Analysiere die folgende Telegram-Nachricht und entscheide, ob es sich um einen persönlichen \
Erfahrungsbericht handelt (jemand beschreibt eine eigene Erfahrung, ein Ergebnis oder eine \
Meinung zu einem Produkt, Kurs oder einer Dienstleistung).

Antworte NUR mit einem JSON-Objekt — kein Markdown, keine Erklärungen:
{{
  "is_testimonial": true,
  "confidence": 0.0,
  "author_name": "",
  "text": "",
  "tags": ""
}}

Regeln für die Felder:

is_testimonial: true wenn Erfahrungsbericht, sonst false.

confidence: deine Sicherheit (0.0 bis 1.0).

author_name:
  1. Suche zuerst im Text nach Mustern wie "Bericht von [Name]", "Bericht via [Name]", "Erfahrung von [Name]", "von [Name]:", "[Name] berichtet", "[Name] schreibt", "Mein Name ist [Name]", o.ä.
  2. Falls gefunden: verwende diesen Namen.
  3. Falls nicht gefunden: verwende den Telegram-Absendernamen.
  4. Format IMMER: nur Vorname + Leerzeichen + erster Buchstabe des Nachnamens + Punkt (z.B. "Peter G." statt "Peter Geisler", "Maria S." statt "Maria Schmidt").
  5. Nie den vollen Nachnamen ausgeben (Datenschutz) und ansonsten auch keine vollständigen Namen.
  6. Emojis im Absendernamen weglassen.

text:
  1. Der bereinigte Erfahrungstext.
  2. Einleitungszeilen wie "Bericht von [Name]:" oder "Erfahrungsbericht von [Name]:" am Anfang entfernen.
  3. #-Zeichen vor Wörtern entfernen, das Wort selbst behalten (aus "#COPD" wird "COPD").
  4. Grußformeln, reine Weiterleitungshinweise und Emojis weglassen.
  5. Falls der Text länger als 400 Zeichen ist: inhaltlich zusammenfassen. Die wichtigsten Aussagen (Symptome, Produkt, Verbesserungen) beibehalten, Wiederholungen und Fülltext weglassen. Maximal 400 Zeichen.
  6. Falls kürzer als 400 Zeichen: unverändert übernehmen.

tags:
  1. Alle #Hashtags aus dem Original-Text als kommagetrennte Liste, ohne #-Zeichen.
  2. Keine Duplikate (Groß-/Kleinschreibung ignorieren).
  3. Beispiel: aus "#COPD" und "#Husten" wird "COPD,Husten".
  4. Falls keine Hashtags vorhanden: leerer String.

Wenn is_testimonial=false: author_name, text und tags als leere Strings zurückgeben.

Absender: {sender_name}
Nachricht:
{message_text}"""

# ── HTTP-Retry ────────────────────────────────────────────────────────────────

# Gemeinsame Session: hält Verbindungen offen (Keep-Alive) und poolt sie, sodass
# die vielen Aufrufe (pro Bericht ein KI-Call + ein POST an CILI) nicht jedes Mal
# einen neuen TCP/TLS-Handshake aufbauen müssen.
_SESSION = requests.Session()


def _http_request_with_retry(method: str, url: str, max_retries: int = 3,
                             base_backoff: float = 1.0, **kwargs) -> requests.Response:
    """HTTP-Request mit exponentiellem Backoff bei transienten Fehlern (Timeout, Connection).

    base_backoff skaliert die Wartezeit zwischen Versuchen (Sekunden vor Jitter/Verdopplung).
    Für Ollama-Calls höher ansetzen als für die schnellen CILI-API-Calls: nach einem
    ReadTimeout braucht ein neu ladendes Modell eher zehn(e) Sekunden als eine."""
    for attempt in range(max_retries):
        try:
            resp = _SESSION.request(method, url, **kwargs)
            resp.raise_for_status()
            return resp
        except (requests.Timeout, requests.ConnectionError) as e:
            if attempt < max_retries - 1:
                # Backoff: base, base*2, base*4 … plus Jitter, um gleichzeitige Retries zu entzerren
                wait = (base_backoff * (2 ** attempt)) + random.uniform(0, base_backoff)
                print(f"  HTTP-Fehler ({e.__class__.__name__}), Retry {attempt + 1}/{max_retries - 1} "
                      f"in {wait:.1f}s …", flush=True)
                time.sleep(wait)
            else:
                raise
    raise RuntimeError(f"max_retries muss > 0 sein (war {max_retries})")


def _http_post_with_retry(url: str, max_retries: int = 3, base_backoff: float = 1.0,
                          **kwargs) -> requests.Response:
    """POST mit Retry (siehe _http_request_with_retry)."""
    return _http_request_with_retry("POST", url, max_retries, base_backoff, **kwargs)


def _http_get_with_retry(url: str, max_retries: int = 3, **kwargs) -> requests.Response:
    """GET mit Retry (siehe _http_request_with_retry)."""
    return _http_request_with_retry("GET", url, max_retries, **kwargs)


# ── Hilfsfunktionen ───────────────────────────────────────────────────────────

def shorten_name(name: str) -> str:
    """'Peter Geisler' → 'Peter G.'  |  bereits kurze Namen bleiben unverändert."""
    name = name.strip()
    parts = name.split()
    if len(parts) >= 2 and len(parts[-1]) > 1:
        return f"{' '.join(parts[:-1])} {parts[-1][0]}."
    return name


_EMOJI_RE = re.compile(
    "["
    "\U0001F000-\U0001FFFF"   # diverse Emoji-Blöcke (Emoticons, Symbole, Flags…)
    "\U00002600-\U000027FF"   # Verschiedene Symbole, Dingbats
    "\U00002B00-\U00002BFF"   # Weitere Symbole
    "\U0000FE00-\U0000FE0F"   # Variationsselektor
    "\U0001F3FB-\U0001F3FF"   # Hautton-Modifier
    "]+",
    flags=re.UNICODE,
)


def post_process_result(result: dict) -> dict:
    """Bereinigt KI-Ausgabe unabhängig davon ob die KI die Regeln befolgt hat."""
    # Autor kürzen (Datenschutz)
    if result.get("author_name"):
        result["author_name"] = shorten_name(result["author_name"])

    # #-Zeichen aus Text entfernen, Emojis entfernen
    if result.get("text"):
        text = re.sub(r"#(\w+)", r"\1", result["text"])
        text = _EMOJI_RE.sub("", text)
        result["text"] = text.strip()

    # Tags: #-Zeichen entfernen, Leerzeichen trimmen, Duplikate entfernen
    if result.get("tags"):
        raw_tags = [t.strip().lstrip("#") for t in result["tags"].split(",") if t.strip()]
        seen: set[str] = set()
        deduped = []
        for tag in raw_tags:
            if tag.lower() not in seen:
                seen.add(tag.lower())
                deduped.append(tag)
        result["tags"] = ",".join(deduped)

    return result


def fix_json_control_chars(s: str) -> str:
    """Maskiert unkodierte Steuerzeichen innerhalb von JSON-Strings.

    LLMs schreiben manchmal echte Newlines in JSON-Stringwerte, was json.loads
    mit 'Invalid control character' abbricht. Diese Funktion korrigiert das.
    """
    result = []
    in_string = False
    escaped = False
    for ch in s:
        if escaped:
            result.append(ch)
            escaped = False
        elif ch == "\\" and in_string:
            result.append(ch)
            escaped = True
        elif ch == '"':
            result.append(ch)
            in_string = not in_string
        elif in_string and ord(ch) < 0x20:
            # Steuerzeichen im String → als JSON-Escape ausgeben
            escapes = {"\n": "\\n", "\r": "\\r", "\t": "\\t"}
            result.append(escapes.get(ch, f"\\u{ord(ch):04x}"))
        else:
            result.append(ch)
    return "".join(result)


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


def classify_with_ai(sender_name: str, message_text: str) -> dict:
    # ftfy repariert kaputtes Unicode (Mojibake) vor dem Versand an die KI
    message_text = ftfy.fix_text(message_text)
    prompt = CLASSIFY_PROMPT.format(sender_name=sender_name, message_text=message_text)
    # Ollamas native Route (/api/generate) statt der OpenAI-kompatiblen (/v1/chat/completions):
    # Über Letztere unterdrückte think:false das Qwen3-Reasoning NICHT zuverlässig — Ollama
    # dachte trotzdem weiter (nur ins separate "reasoning"-Feld statt "content"), was wiederholt
    # das gesamte max_tokens-Budget aufbrauchte, bevor content geschrieben wurde (auch nach
    # Erhöhung von 800 auf 4000 auf 8192 — siehe Git-History). Über /api/generate wird
    # think:false zuverlässig respektiert, siehe analyze_worker.py für dasselbe Modell/Problem.
    # Kehrseite: bricht Kompatibilität zu OpenAI-artigen Backends wie LM Studio.
    print(f"  Sende an {AI_URL} (Modell: {AI_MODEL}, GPU-Temp={gpu_temperature()}°C)")
    resp = _http_post_with_retry(
        f"{AI_URL}/api/generate",
        base_backoff=AI_RETRY_BACKOFF_SECONDS,
        json={
            "model": AI_MODEL,
            "prompt": prompt,
            "stream": False,
            "think": False,
            "options": {
                "temperature": 0.1,
                "num_ctx": AI_NUM_CTX,
                "num_predict": AI_NUM_PREDICT,
            },
        },
        timeout=AI_TIMEOUT,
    )
    body = resp.json()
    if body.get("done_reason") == "length":
        print(f"  WARNUNG: KI-Antwort durch num_predict={AI_NUM_PREDICT} abgeschnitten "
              f"(done_reason=length)")
    raw = body["response"].strip()
    if "```" in raw:
        parts = raw.split("```")
        raw = parts[1].removeprefix("json").strip() if len(parts) > 1 else raw
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        try:
            return json.loads(fix_json_control_chars(raw))
        except json.JSONDecodeError:
            raise json.JSONDecodeError(
                f"{exc.msg} (raw response: {raw[:200]!r}, full body: {json.dumps(body)[:1000]!r})",
                exc.doc, exc.pos
            ) from exc


_URL_RE = re.compile(r"https?://[^\s\)\]\>\"']+", re.IGNORECASE)

def extract_video_url(text: str) -> str | None:
    """Gibt die erste HTTP-URL aus dem Text zurück (yt-dlp prüft selbst ob sie downloadbar ist)."""
    m = _URL_RE.search(text)
    return m.group(0).rstrip(".,;:!?") if m else None

def _upload_webinar(url: str, folder_id: int, token: str, msg_date: datetime) -> None:
    _download_and_upload_video(
        url            = url,
        folder_id      = folder_id,
        token          = token,
        cili_url       = CILI_URL,
        title_fallback = f"Lifestyle Webinar {msg_date.strftime('%Y-%m-%d')}",
        max_height     = WEBINAR_MAX_HEIGHT,
        pin_to_top     = True,
    )




def get_cili_token() -> str:
    """Nutzt ein von TelegramImportService injiziertes Job-Token (CILI_TOKEN, 4h gültig),
    falls vorhanden — sonst Login per Username/Passwort (15-Min-Token, nur für manuellen
    CLI-Aufruf gedacht; reicht bei langen Webinar-Downloads/-Uploads nicht aus)."""
    env_token = os.getenv("CILI_TOKEN")
    if env_token:
        return env_token
    resp = _http_post_with_retry(
        f"{CILI_URL}/api/auth/login",
        json={"username": CILI_USER, "password": CILI_PASS},
        timeout=15,
    )
    return resp.json()["accessToken"]


def get_last_cili_date(token: str) -> datetime | None:
    """Gibt das createdAt des aktuellsten CILI-Erfahrungsberichts zurück (UTC, ohne tzinfo)."""
    resp = _http_get_with_retry(
        f"{CILI_URL}/api/testimonials",
        headers={"Authorization": f"Bearer {token}"},
        params={"page": 0, "size": 1, "source": TG_SOURCE},
        timeout=15,
    )
    content = resp.json().get("content", [])
    if not content:
        return None
    # createdAt ist ISO 8601 ohne Timezone — in CILI immer als UTC gespeichert
    return datetime.fromisoformat(content[0]["createdAt"])


def post_testimonial(token: str, author: str, text: str, tags: str,
                     created_at: datetime, images: list) -> dict:
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "authorName": author[:200],
        "text": text[:50000],
        "createdAt": created_at.strftime("%Y-%m-%dT%H:%M:%S"),
        "human": "true" if TG_IS_HUMAN else "false",
        "animal": "true" if TG_IS_ANIMAL else "false",
    }
    if tags:
        data["tags"] = tags[:500]

    files = [
        ("images", (f"bild_{i + 1}.jpg", img, "image/jpeg"))
        for i, img in enumerate(images)
    ]
    if not files:
        # requests benötigt mindestens einen file-Eintrag für multipart/form-data
        files = [("_mp", ("", b""))]

    resp = _http_post_with_retry(
        f"{CILI_URL}/api/testimonials",
        headers=headers,
        data=data,
        files=files,
        timeout=30,
    )
    return resp.json()


# ── Hauptlogik ─────────────────────────────────────────────────────────────────

def _to_utc_naive(dt: datetime) -> datetime:
    """Telegram-Zeit → UTC ohne tzinfo (CILI speichert naive UTC)."""
    return dt.astimezone(timezone.utc).replace(tzinfo=None)


def _validate_config() -> None:
    """Prüft die Pflicht-Konfiguration; bricht bei fehlenden Werten mit Hinweis ab."""
    if TG_API_ID == 0 or not TG_API_HASH:
        sys.exit(
            "Fehler: TG_API_ID und TG_API_HASH müssen gesetzt sein.\n"
            "Zugangsdaten unter https://my.telegram.org/apps erstellen."
        )
    if not TG_GROUP:
        sys.exit("Fehler: TG_GROUP muss gesetzt sein (Gruppenname, -link oder numerische ID).")
    if not TG_SOURCE:
        sys.exit("Fehler: TG_SOURCE muss gesetzt sein (exakt 'Mensch' oder 'Tier').")
    if not TG_IS_HUMAN and not TG_IS_ANIMAL:
        sys.exit(f"Fehler: TG_SOURCE muss 'Mensch' oder 'Tier' sein (aktuell: '{TG_SOURCE}').")


async def _resolve_group(client: TelegramClient):
    """Löst TG_GROUP (Name, Link oder numerische ID) zur Telethon-Entity auf."""
    # Dialogs laden, damit Telethon numerische IDs im Cache hat
    await client.get_dialogs()
    entity_arg = int(TG_GROUP) if TG_GROUP.lstrip('-').isdigit() else TG_GROUP
    try:
        return await client.get_entity(entity_arg)
    except Exception as exc:
        sys.exit(f"Fehler: Telegram-Gruppe nicht gefunden ({TG_GROUP!r}): {exc}")


def _resolve_cutoff(token: str, manual_cutoff: datetime | None) -> datetime | None:
    """Bestimmt das Cutoff-Datum: CLI-Argument hat Vorrang, dann State-Datei, dann letzter CILI-Eintrag."""
    if manual_cutoff:
        print(f"Cutoff (manuell): {manual_cutoff.strftime('%d.%m.%Y')}")
        return manual_cutoff

    state_date      = _load_state_date()
    testimonial_date = get_last_cili_date(token)

    if state_date and testimonial_date:
        cutoff = max(state_date, testimonial_date)
    else:
        cutoff = state_date or testimonial_date

    if cutoff:
        source = "State-Datei" if cutoff == state_date and cutoff != testimonial_date else "letzter CILI-Eintrag"
        print(f"Cutoff ({source}): {cutoff.strftime('%d.%m.%Y %H:%M:%S')} UTC")
    else:
        print("CILI ist leer — importiere alle Nachrichten.")
        print("Tipp: --from-date YYYY-MM-DD verwenden um einen Startzeitpunkt zu setzen.")
    return cutoff


async def _fetch_new_messages(client: TelegramClient, group, cutoff: datetime | None) -> list:
    """Nachrichten neueste-zuerst holen und abbrechen, sobald der Cutoff erreicht ist.

    So wird nur ein kleiner Bereich am Ende der Gruppe gelesen — kein kompletter Scan."""
    new_messages = []
    async for message in client.iter_messages(group):
        if not message.text and not message.media:
            continue
        if cutoff and _to_utc_naive(message.date) <= cutoff:
            break
        new_messages.append(message)
    return new_messages


def _group_albums(messages: list) -> dict:
    """Album-Nachrichten (mehrere Fotos) nach grouped_id bündeln, damit alle Bilder
    eines Albums zusammen importiert werden."""
    album_map: dict = defaultdict(list)
    for msg in messages:
        if msg.grouped_id:
            album_map[msg.grouped_id].append(msg)
    return album_map


def _resolve_message_content(message, album_map: dict, processed_group_ids: set):
    """Liefert (text, media_msgs) für eine Nachricht bzw. ihr Album.

    Bei einem bereits verarbeiteten Album-Mitglied wird (None, None) zurückgegeben."""
    if not message.grouped_id:
        return (message.text or "").strip(), [message]
    if message.grouped_id in processed_group_ids:
        return None, None  # zweites/weiteres Album-Mitglied — beim ersten erledigt
    processed_group_ids.add(message.grouped_id)
    group_msgs = album_map[message.grouped_id]
    # Text aus dem Album-Mitglied nehmen, das einen hat (meist das erste)
    text = next((m.text for m in group_msgs if m.text), "").strip()
    return text, group_msgs


def _is_webinar(text: str) -> bool:
    """True für Lifestyle-Webinar-Nachrichten (keine Erfahrungsberichte → Video-Import)."""
    return re.search(r"lifestyle\s+#?webinar", text, re.IGNORECASE) is not None


def _try_import_webinar(text: str, msg_utc: datetime) -> None:
    """Lädt das im Text verlinkte Webinar-Video hoch (falls URL + Zielordner vorhanden).

    Holt unmittelbar vor dem Upload ein frisches Token: Video-Downloads dauern oft
    länger als die 15-minütige Token-TTL, ein zu Laufbeginn geholtes Token wäre beim
    tatsächlichen Upload (POST /api/uploads/init) sonst bereits abgelaufen (401)."""
    video_url = extract_video_url(text)
    if not (video_url and WEBINAR_FOLDER_ID):
        return
    try:
        upload_token = get_cili_token()
        _upload_webinar(video_url, int(WEBINAR_FOLDER_ID), upload_token, msg_utc)
    except Exception as exc:
        print(f"  Webinar-Video fehlgeschlagen: {exc}")


async def _resolve_sender_name(message) -> str:
    """Anzeigename des Absenders: Vor-/Nachname, sonst Username, sonst 'Unbekannt'."""
    sender = await message.get_sender()
    return (
        " ".join(filter(None, [
            getattr(sender, "first_name", ""),
            getattr(sender, "last_name", ""),
        ])) or getattr(sender, "username", "Unbekannt")
    )


def _classify(sender_name: str, text: str) -> dict | None:
    """KI-Klassifikation samt Nachbearbeitung; None bei Fehler (übersprungen)."""
    try:
        return post_process_result(classify_with_ai(sender_name, text))
    except Exception as exc:
        print(f"  KI-Fehler: {exc} — übersprungen\n")
        return None


async def _download_images(client: TelegramClient, media_msgs: list) -> list:
    """Lädt alle Foto-Anhänge der Nachricht(en) als Bytes herunter."""
    images = []
    for m in media_msgs:
        if not m.photo:
            continue
        try:
            buf = BytesIO()
            await client.download_media(m.media, file=buf)
            images.append(buf.getvalue())
            print(f"  Bild heruntergeladen ({len(images[-1])} Bytes)")
        except Exception as exc:
            print(f"  Bild-Download fehlgeschlagen: {exc}")
    return images


def _post_one(token: str, author: str, result: dict, text: str,
              msg_utc: datetime, images: list) -> str:
    """Postet einen Erfahrungsbericht an CILI. Liefert 'imported' oder 'failed'."""
    try:
        created = post_testimonial(
            token=token,
            author=author,
            text=(result.get("text") or text).strip(),
            tags=result.get("tags", ""),
            created_at=msg_utc,
            images=images,
        )
        print(f"  ✓ Importiert als Erfahrungsbericht ID {created['id']}\n")
        return "imported"
    except requests.HTTPError as exc:
        print(f"  CILI-Fehler {exc.response.status_code}: {exc.response.text[:200]} — übersprungen\n")
        return "failed"
    except Exception as exc:
        print(f"  CILI-Fehler: {exc} — übersprungen\n")
        return "failed"


async def _process_message(client: TelegramClient, message, album_map: dict,
                           processed_group_ids: set, token: str) -> str:
    """Verarbeitet eine Nachricht (bzw. ein Album) Ende-zu-Ende.

    Rückgabe-Status: 'imported', 'skipped', 'failed' oder 'album_dup'."""
    text, media_msgs = _resolve_message_content(message, album_map, processed_group_ids)
    if text is None:
        return "album_dup"
    if len(text) < 50:
        return "skipped"

    msg_utc = _to_utc_naive(message.date)

    if _is_webinar(text):
        _try_import_webinar(text, msg_utc)
        return "skipped"

    sender_name = await _resolve_sender_name(message)
    print(f"[{msg_utc.strftime('%d.%m.%Y %H:%M')}] {sender_name}: "
          f"{text[:100].replace(chr(10), ' ')}…")

    result = _classify(sender_name, text)
    if result is None:
        return "skipped"

    confidence = float(result.get("confidence", 0))
    if not result.get("is_testimonial") or confidence < MIN_CONFIDENCE:
        print(f"  → Kein Erfahrungsbericht (confidence={confidence:.2f})\n")
        return "skipped"

    print(f"  → Erfahrungsbericht erkannt (confidence={confidence:.2f})")

    final_text = (result.get("text") or text).strip()
    if len(final_text.split()) < 10:
        print(f"  → Zu kurz nach KI-Verarbeitung ({final_text!r}) — übersprungen\n")
        return "skipped"

    images = await _download_images(client, media_msgs)
    author = (result.get("author_name") or sender_name).strip() or sender_name
    return _post_one(token, author, result, text, msg_utc, images)


async def main():
    _validate_config()

    manual_cutoff = (
        datetime.strptime(_args.from_date, "%Y-%m-%d") if _args.from_date else None
    )

    def _prompt_phone() -> str:
        return input("Telefonnummer: ")

    async with TelegramClient(
        SESSION_FILE, TG_API_ID, TG_API_HASH,
        connection_retries=3,
        retry_delay=5,
    ) as client:
        await client.start(phone=TG_PHONE or _prompt_phone)

        group = await _resolve_group(client)
        print(f"Gruppe: {getattr(group, 'title', TG_GROUP)}")

        token = get_cili_token()
        print("CILI-Login erfolgreich.")

        cutoff = _resolve_cutoff(token, manual_cutoff)
        print()

        new_messages = await _fetch_new_messages(client, group, cutoff)
        if not new_messages:
            print("Keine neuen Nachrichten seit dem letzten Import.")
            return

        print(f"{len(new_messages)} neue Nachricht(en) gefunden — verarbeite älteste zuerst.\n")

        album_map = _group_albums(new_messages)
        processed_group_ids: set = set()
        counts = {"imported": 0, "skipped": 0}
        run_started = time.monotonic()
        processed = 0
        limit_hit = None

        # Umkehren: älteste zuerst → chronologische Reihenfolge in CILI
        for message in reversed(new_messages):
            if processed >= MAX_MESSAGES_PER_RUN:
                limit_hit = f"{MAX_MESSAGES_PER_RUN} Nachrichten"
                break
            if (time.monotonic() - run_started) / 60 >= MAX_RUNTIME_MINUTES:
                limit_hit = f"{MAX_RUNTIME_MINUTES:.0f} Minuten Laufzeit"
                break

            status = await _process_message(
                client, message, album_map, processed_group_ids, token
            )
            processed += 1
            if status in counts:
                counts[status] += 1
            # Nach jeder Nachricht speichern (nicht erst am Lauf-Ende): große Webinar-Videos
            # können den 60-Min-Prozess-Timeout überschreiten und den Import hart abbrechen
            # (process.destroyForcibly() in TelegramImportService). Ohne Zwischenstand würde
            # der nächste Lauf bereits erfolgreich hochgeladene Videos erneut importieren.
            _save_state_date(_to_utc_naive(message.date))

        if limit_hit:
            print(f"Limit erreicht ({limit_hit}) — {len(new_messages) - processed} "
                  f"verbleibende Nachricht(en) folgen im nächsten Lauf.\n")

        print(f"Fertig: {counts['imported']} importiert, {counts['skipped']} übersprungen.")


if __name__ == "__main__":
    asyncio.run(main())
