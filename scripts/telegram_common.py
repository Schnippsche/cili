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
import asyncio
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
from telethon.errors import FloodWaitError
from telethon.tl.custom.message import Message

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
    """Naiv (kein tzinfo) — wie load_state_date()/to_utc_naive() wird der Cutoff
    im gesamten Modul als UTC ohne tzinfo behandelt, per Konvention statt Prüfung."""
    return datetime.strptime(from_date, "%Y-%m-%d") if from_date else None


# ── State-Datei ───────────────────────────────────────────────────────────────

def load_state_date(state_file: Path) -> datetime | None:
    """Letzter erfolgreich verarbeiteter Telegram-Zeitstempel (UTC, naiv)."""
    try:
        return datetime.fromisoformat(state_file.read_text(encoding="utf-8").strip())
    except (FileNotFoundError, ValueError):
        return None


def save_state_date(state_file: Path, dt: datetime) -> None:
    """Schreibt atomar (tmp-Datei + Path.replace), damit ein Absturz mitten im
    Schreibvorgang nicht die State-Datei beschädigt zurücklässt."""
    tmp_file = state_file.with_suffix(state_file.suffix + ".tmp")
    tmp_file.write_text(dt.isoformat(), encoding="utf-8")
    tmp_file.replace(state_file)


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

HTTP_DEFAULT_TIMEOUT = (5, 60)  # (Connect, Read) Sekunden — gilt nur falls kwargs keins setzt
HTTP_MAX_RETRIES = 3
HTTP_BASE_BACKOFF = 1.0
# Transiente Statuscodes: Retry lohnt sich (Überlastung/Wartung), im Gegensatz zu
# 4xx-Fehlern wie 401/404, die bei erneutem Versuch identisch fehlschlagen würden.
HTTP_RETRYABLE_STATUS_CODES = frozenset({429, 500, 502, 503, 504})


def _retry_after_seconds(resp: requests.Response) -> float | None:
    """Retry-After-Header als Sekunden, falls vorhanden und numerisch (HTTP-Date-Form
    wird nicht unterstützt — dafür greift der normale exponentielle Backoff)."""
    value = resp.headers.get("Retry-After")
    if not value:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def http_request_with_retry(method: str, url: str, max_retries: int = HTTP_MAX_RETRIES,
                            base_backoff: float = HTTP_BASE_BACKOFF, **kwargs) -> requests.Response:
    """HTTP-Request mit exponentiellem Backoff bei transienten Fehlern: Timeout,
    Connection-Fehler sowie HTTP_RETRYABLE_STATUS_CODES (429/500/502/503/504).
    Berücksichtigt den Retry-After-Header falls vorhanden. Setzt HTTP_DEFAULT_TIMEOUT
    falls kein `timeout` in kwargs übergeben wurde — ein Request darf nie unbegrenzt hängen."""
    kwargs.setdefault("timeout", HTTP_DEFAULT_TIMEOUT)
    for attempt in range(max_retries):
        is_last = attempt == max_retries - 1
        try:
            resp = _SESSION.request(method, url, **kwargs)
            if not is_last and resp.status_code in HTTP_RETRYABLE_STATUS_CODES:
                wait = _retry_after_seconds(resp)
                if wait is None:
                    wait = (base_backoff * (2 ** attempt)) + random.uniform(0, base_backoff)
                print(f"  HTTP-Fehler ({resp.status_code}), Retry {attempt + 1}/{max_retries - 1} "
                      f"in {wait:.1f}s …", flush=True)
                time.sleep(wait)
                continue
            resp.raise_for_status()
            return resp
        except (requests.Timeout, requests.ConnectionError) as e:
            if is_last:
                raise
            wait = (base_backoff * (2 ** attempt)) + random.uniform(0, base_backoff)
            print(f"  HTTP-Fehler ({e.__class__.__name__}), Retry {attempt + 1}/{max_retries - 1} "
                  f"in {wait:.1f}s …", flush=True)
            time.sleep(wait)
    raise RuntimeError(f"max_retries muss > 0 sein (war {max_retries})")


def http_post_with_retry(url: str, max_retries: int = HTTP_MAX_RETRIES,
                         base_backoff: float = HTTP_BASE_BACKOFF, **kwargs) -> requests.Response:
    return http_request_with_retry("POST", url, max_retries, base_backoff, **kwargs)


def http_get_with_retry(url: str, max_retries: int = HTTP_MAX_RETRIES, **kwargs) -> requests.Response:
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


async def _with_flood_retry(coro_fn):
    """Führt eine Telethon-Coroutine-Factory aus; bei FloodWaitError einmalig die
    vorgegebene Wartezeit abwarten und erneut versuchen (statt abzubrechen)."""
    try:
        return await coro_fn()
    except FloodWaitError as exc:
        print(f"  Flood-Wait: warte {exc.seconds}s …", flush=True)
        await asyncio.sleep(exc.seconds)
        return await coro_fn()


async def resolve_group(client: TelegramClient, tg_group: str):
    """Löst TG_GROUP (Name, Link oder numerische ID) zur Telethon-Entity auf.

    Versucht zuerst get_entity() direkt (spart die RPC-Kosten für den kompletten
    Dialog-Katalog); nur falls die Entity dem Client noch unbekannt ist
    (ValueError), wird als Fallback get_dialogs() geladen (füllt Telethons
    Entity-Cache) und erneut versucht."""
    try:
        entity_arg = int(tg_group)
    except ValueError:
        entity_arg = tg_group
    try:
        return await _with_flood_retry(lambda: client.get_entity(entity_arg))
    except ValueError:
        pass
    await client.get_dialogs()
    try:
        return await _with_flood_retry(lambda: client.get_entity(entity_arg))
    except Exception as exc:
        sys.exit(f"Fehler: Telegram-Gruppe nicht gefunden ({tg_group!r}): {exc}")


async def fetch_new_messages(client: TelegramClient, group, cutoff: datetime | None) -> list[Message]:
    """Nachrichten neueste-zuerst holen und abbrechen, sobald der Cutoff erreicht ist.

    So wird nur ein kleiner Bereich am Ende der Gruppe gelesen — kein kompletter Scan.
    Läuft komplett in eine Liste ein (statt zu yielden): Aufrufer brauchen vorab
    len() und group_albums() über den vollständigen Bereich sowie reversed() für die
    chronologische Verarbeitungsreihenfolge. Telethons iter_messages() behandelt
    FloodWaitError bis zum client-seitigen flood_sleep_threshold intern bereits
    automatisch (Default 60s)."""
    new_messages = []
    async for message in client.iter_messages(group):
        if not message.text and not message.media:
            continue
        if cutoff and to_utc_naive(message.date) <= cutoff:
            break
        new_messages.append(message)
    return new_messages


async def resolve_sender_name(message: Message) -> str:
    """Anzeigename des Absenders: Vor-/Nachname, sonst Username, sonst 'Unbekannt'."""
    sender = await _with_flood_retry(message.get_sender)
    return (
        " ".join(filter(None, [
            getattr(sender, "first_name", ""),
            getattr(sender, "last_name", ""),
        ])) or getattr(sender, "username", "Unbekannt")
    )


async def download_media_to_dir(client: TelegramClient, msg: Message, tmp_dir: Path) -> Path | None:
    """Lädt einen Video-/Audio-Anhang in ein Verzeichnis (Telethon vergibt
    Dateiname/Endung automatisch). None bei Fehler (geloggt, übersprungen)."""
    try:
        path_str = await _with_flood_retry(lambda: client.download_media(msg, file=str(tmp_dir) + os.sep))
        if not path_str:
            return None
        path = Path(path_str)
        print(f"  Video/Audio heruntergeladen: {path.name} ({path.stat().st_size} Bytes)")
        return path
    except Exception as exc:
        print(f"  Video/Audio-Download fehlgeschlagen: {exc}")
        return None


def group_albums(messages: list[Message]) -> dict[int, list[Message]]:
    """Album-Nachrichten (mehrere Fotos) nach grouped_id bündeln, damit alle Bilder
    eines Albums zusammen importiert werden. Speichert nur Referenzen auf dieselben
    Message-Objekte aus `messages` — keine Kopien, kein relevanter Mehrverbrauch."""
    album_map: dict[int, list[Message]] = defaultdict(list)
    for msg in messages:
        if msg.grouped_id:
            album_map[msg.grouped_id].append(msg)
    return album_map


def classify_media(msg: Message) -> str | None:
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
_WEBINAR_RE = re.compile(r"lifestyle\s+#?webinar", re.IGNORECASE)


def is_webinar(text: str) -> bool:
    """True für Lifestyle-Webinar-Nachrichten (keine Erfahrungsberichte)."""
    return _WEBINAR_RE.search(text) is not None


def extract_video_url(text: str) -> str | None:
    """Gibt die erste HTTP-URL aus dem Text zurück (yt-dlp prüft selbst ob sie downloadbar ist)."""
    m = _URL_RE.search(text)
    return m.group(0).rstrip(".,;:!?") if m else None
