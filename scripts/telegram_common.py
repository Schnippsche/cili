"""
Gemeinsame Bausteine für die drei Telegram-Import-Skripte
(telegram_import_mensch.py, telegram_import_tier.py, telegram_import_webinare.py):
CLI-Argumente, State-Datei, HTTP-Retry, Telegram-Client-Hilfsfunktionen,
CILI-Login, Medien-Klassifikation und Webinar-Erkennung.

Enthält bewusst KEINE Modul-Level-Konfiguration aus os.getenv() — jedes
Einstiegsskript liest seine eigene .env-Datei und reicht die Werte explizit
an die Funktionen hier weiter (gleiches Muster wie video_upload.py).
"""

import argparse
import os
import random
import re
import sys
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import requests
from dotenv import load_dotenv
from telethon import TelegramClient

# ── CLI / .env ────────────────────────────────────────────────────────────────

def parse_args(description: str) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--from-date", metavar="YYYY-MM-DD",
                        help="Startdatum für den Import (Standard: State-Datei)")
    parser.add_argument("--env", metavar="PFAD",
                        help="Pfad zur .env-Datei (Standard: .env im Arbeitsverzeichnis)")
    return parser.parse_args()


def load_env(env_arg: str | None) -> None:
    if env_arg:
        env_file = Path(env_arg)
        if not env_file.exists():
            sys.exit(f"Fehler: .env-Datei nicht gefunden: {env_file}")
        load_dotenv(env_file)
    else:
        load_dotenv()


def parse_manual_cutoff(from_date: str | None) -> datetime | None:
    return datetime.strptime(from_date, "%Y-%m-%d") if from_date else None


# ── State-Datei ───────────────────────────────────────────────────────────────

def load_state_date(state_file: Path) -> datetime | None:
    """Letzter erfolgreich verarbeiteter Telegram-Zeitstempel (UTC, naiv)."""
    try:
        return datetime.fromisoformat(state_file.read_text().strip())
    except (FileNotFoundError, ValueError):
        return None


def save_state_date(state_file: Path, dt: datetime) -> None:
    state_file.write_text(dt.isoformat())


def resolve_cutoff(state_file: Path, manual_cutoff: datetime | None) -> datetime | None:
    """Cutoff-Datum: CLI-Argument hat Vorrang, sonst State-Datei. Kein DB-Fallback
    mehr — geht die State-Datei verloren, muss --from-date manuell gesetzt werden."""
    if manual_cutoff:
        print(f"Cutoff (manuell): {manual_cutoff.strftime('%d.%m.%Y')}")
        return manual_cutoff
    state_date = load_state_date(state_file)
    if state_date:
        print(f"Cutoff (State-Datei): {state_date.strftime('%d.%m.%Y %H:%M:%S')} UTC")
    else:
        print("Keine State-Datei vorhanden — importiere alle Nachrichten.")
        print("Tipp: --from-date YYYY-MM-DD verwenden um einen Startzeitpunkt zu setzen.")
    return state_date


# ── HTTP-Retry ────────────────────────────────────────────────────────────────

# Gemeinsame Session: hält Verbindungen offen (Keep-Alive) und poolt sie.
_SESSION = requests.Session()


def http_request_with_retry(method: str, url: str, max_retries: int = 3,
                            base_backoff: float = 1.0, **kwargs) -> requests.Response:
    """HTTP-Request mit exponentiellem Backoff bei transienten Fehlern (Timeout, Connection)."""
    for attempt in range(max_retries):
        try:
            resp = _SESSION.request(method, url, **kwargs)
            resp.raise_for_status()
            return resp
        except (requests.Timeout, requests.ConnectionError) as e:
            if attempt < max_retries - 1:
                wait = (base_backoff * (2 ** attempt)) + random.uniform(0, base_backoff)
                print(f"  HTTP-Fehler ({e.__class__.__name__}), Retry {attempt + 1}/{max_retries - 1} "
                      f"in {wait:.1f}s …", flush=True)
                time.sleep(wait)
            else:
                raise
    raise RuntimeError(f"max_retries muss > 0 sein (war {max_retries})")


def http_post_with_retry(url: str, max_retries: int = 3, base_backoff: float = 1.0,
                         **kwargs) -> requests.Response:
    return http_request_with_retry("POST", url, max_retries, base_backoff, **kwargs)


def http_get_with_retry(url: str, max_retries: int = 3, **kwargs) -> requests.Response:
    return http_request_with_retry("GET", url, max_retries, **kwargs)


# ── CILI-Login ────────────────────────────────────────────────────────────────

def get_cili_token(cili_url: str, user: str, password: str, env_token: str | None = None) -> str:
    """Nutzt ein von TelegramImportService injiziertes Job-Token (CILI_TOKEN, 4h gültig),
    falls vorhanden — sonst Login per Username/Passwort (15-Min-Token, nur für manuellen
    CLI-Aufruf gedacht; reicht bei langen Webinar-Downloads/-Uploads nicht aus)."""
    if env_token:
        return env_token
    resp = http_post_with_retry(
        f"{cili_url}/api/auth/login",
        json={"username": user, "password": password},
        timeout=15,
    )
    return resp.json()["accessToken"]


# ── Zeit ──────────────────────────────────────────────────────────────────────

def to_utc_naive(dt: datetime) -> datetime:
    """Telegram-Zeit → UTC ohne tzinfo (CILI speichert naive UTC)."""
    return dt.astimezone(timezone.utc).replace(tzinfo=None)


# ── Telegram-Client ───────────────────────────────────────────────────────────

def build_client(session_file: Path, api_id: int, api_hash: str) -> TelegramClient:
    return TelegramClient(str(session_file), api_id, api_hash, connection_retries=3, retry_delay=5)


async def resolve_group(client: TelegramClient, tg_group: str):
    """Löst TG_GROUP (Name, Link oder numerische ID) zur Telethon-Entity auf."""
    await client.get_dialogs()
    entity_arg = int(tg_group) if tg_group.lstrip('-').isdigit() else tg_group
    try:
        return await client.get_entity(entity_arg)
    except Exception as exc:
        sys.exit(f"Fehler: Telegram-Gruppe nicht gefunden ({tg_group!r}): {exc}")


async def fetch_new_messages(client: TelegramClient, group, cutoff: datetime | None) -> list:
    """Nachrichten neueste-zuerst holen und abbrechen, sobald der Cutoff erreicht ist.

    So wird nur ein kleiner Bereich am Ende der Gruppe gelesen — kein kompletter Scan."""
    new_messages = []
    async for message in client.iter_messages(group):
        if not message.text and not message.media:
            continue
        if cutoff and to_utc_naive(message.date) <= cutoff:
            break
        new_messages.append(message)
    return new_messages


async def resolve_sender_name(message) -> str:
    """Anzeigename des Absenders: Vor-/Nachname, sonst Username, sonst 'Unbekannt'."""
    sender = await message.get_sender()
    return (
        " ".join(filter(None, [
            getattr(sender, "first_name", ""),
            getattr(sender, "last_name", ""),
        ])) or getattr(sender, "username", "Unbekannt")
    )


async def download_media_to_dir(client: TelegramClient, msg, tmp_dir: Path) -> Path | None:
    """Lädt einen Video-/Audio-Anhang in ein Verzeichnis (Telethon vergibt
    Dateiname/Endung automatisch). None bei Fehler (geloggt, übersprungen)."""
    try:
        path_str = await client.download_media(msg, file=str(tmp_dir) + os.sep)
        if not path_str:
            return None
        path = Path(path_str)
        print(f"  Video/Audio heruntergeladen: {path.name} ({path.stat().st_size} Bytes)")
        return path
    except Exception as exc:
        print(f"  Video/Audio-Download fehlgeschlagen: {exc}")
        return None


def group_albums(messages: list) -> dict:
    """Album-Nachrichten (mehrere Fotos) nach grouped_id bündeln, damit alle Bilder
    eines Albums zusammen importiert werden."""
    album_map: dict = defaultdict(list)
    for msg in messages:
        if msg.grouped_id:
            album_map[msg.grouped_id].append(msg)
    return album_map


def classify_media(msg) -> str | None:
    """'image' | 'video' | 'audio' | None.

    Telethons `audio`-Property schließt Voice-Notes bereits selbst aus
    (Bedingung `not attr.voice`) — `video` dagegen NICHT: `video` prüft nur
    auf ein DocumentAttributeVideo ohne jede Bedingung und ist daher auch bei
    runden Video-Notes wahr (die eigene `video_note`-Property filtert erst
    separat auf `attr.round_message`). Runde Video-Notes werden deshalb
    explizit ausgeschlossen. Aus demselben Grund sind GIFs (tragen zusätzlich
    DocumentAttributeAnimated, `msg.gif`) und Video-Sticker (zusätzlich
    DocumentAttributeSticker, `msg.sticker`) ebenfalls `video`-truthy, aber
    keine echten Video-Anhänge — beide werden deshalb ebenfalls ausgeschlossen."""
    if msg.photo:
        return "image"
    if msg.video and not msg.video_note and not msg.gif and not msg.sticker:
        return "video"
    if msg.audio:
        return "audio"
    return None


# ── Webinar-Erkennung ─────────────────────────────────────────────────────────

_URL_RE = re.compile(r"https?://[^\s\)\]\>\"']+", re.IGNORECASE)


def is_webinar(text: str) -> bool:
    """True für Lifestyle-Webinar-Nachrichten (keine Erfahrungsberichte)."""
    return re.search(r"lifestyle\s+#?webinar", text, re.IGNORECASE) is not None


def extract_video_url(text: str) -> str | None:
    """Gibt die erste HTTP-URL aus dem Text zurück (yt-dlp prüft selbst ob sie downloadbar ist)."""
    m = _URL_RE.search(text)
    return m.group(0).rstrip(".,;:!?") if m else None
