#!/usr/bin/env python3
"""
Video-Download und Upload in einen CILI-Ordner.

Verwendung:
  python scripts/video_upload.py <URL> --folder-id <ID> [Optionen]

Optionen:
  --folder-id <ID>      CILI-Ordner-ID (Pflicht)
  --title <Titel>       Titel/Dateiname (Standard: aus Video-Metadaten oder URL-Basename)
  --max-height <px>     Maximale Auflösung beim Download (Standard: 720)
  --env <Pfad>          Pfad zur .env-Datei (Standard: .env im aktuellen Verzeichnis)

Beispiel:
  python scripts/video_upload.py https://www.loom.com/share/abc123 --folder-id 42
  python scripts/video_upload.py https://youtu.be/xyz --folder-id 7 --title "Webinar Juni"
"""

import argparse
import mimetypes
import os
import random
import re
import shutil
import sys
import tempfile
import time
from pathlib import Path

import requests
from dotenv import load_dotenv

from urllib.parse import urlparse

# Windows-Konsolen nutzen teils cp1252; UTF-8 erzwingen, damit Umlaute/Symbole in
# print() keinen UnicodeEncodeError auslösen (der Java-Dienst liest stdout als UTF-8).
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

# ── Konfiguration aus Umgebung ─────────────────────────────────────────────────

def _load_env(env_path: str | None = None) -> None:
    if env_path:
        p = Path(env_path)
        if not p.exists():
            sys.exit(f"Fehler: .env-Datei nicht gefunden: {p}")
        load_dotenv(p)
    else:
        load_dotenv()


def _cili_config() -> tuple[str, str, str]:
    url  = os.getenv("CILI_URL",  "http://localhost:8080")
    user = os.getenv("CILI_USER", "admin")
    pw   = os.getenv("CILI_PASS", "admin")
    return url, user, pw


def _cili_token_from_env() -> str | None:
    """Gibt ein bereits vorhandenes JWT-Token zurück (gesetzt von CILI_TOKEN), oder None."""
    return os.getenv("CILI_TOKEN")

# ── HTTP-Retry ────────────────────────────────────────────────────────────────

def _with_retry(fn, max_retries: int = 3, label: str = ""):
    """Führt fn() aus und wiederholt bei transienten Netzwerkfehlern mit Backoff."""
    for attempt in range(max_retries):
        try:
            resp = fn()
            resp.raise_for_status()
            return resp
        except (requests.Timeout, requests.ConnectionError) as e:
            if attempt < max_retries - 1:
                wait = (2 ** attempt) + random.uniform(0, 1)
                info = f" ({label})" if label else ""
                print(f"  Netzwerkfehler{info}: {e.__class__.__name__}, "
                      f"Retry {attempt + 1}/{max_retries - 1} in {wait:.1f}s …", flush=True)
                time.sleep(wait)
            else:
                raise


# ── Hilfsfunktionen ───────────────────────────────────────────────────────────

_INVALID_CHARS = re.compile(r'[\\/*?:"<>|]')

CHUNK_SIZE = 5 * 1024 * 1024   # 5 MB
_MIN_CHUNK_SIZE = 1 * 1024 * 1024    # 1 MB
_MAX_CHUNK_SIZE = 100 * 1024 * 1024  # 100 MB

# Deno als JS-Runtime für yt-dlps n-challenge/po_token-Solver (nötig, damit YouTube
# mit Cookies bzw. Web-Client volle Formate liefert). None → nicht installiert, dann
# läuft der Download über die Mobile-Clients ohne Cookies (öffentliche Videos ok).
_DENO_PATH = shutil.which("deno")


def sanitize_filename(name: str) -> str:
    """Entfernt Zeichen die in Dateinamen nicht erlaubt sind."""
    return _INVALID_CHARS.sub("_", name).strip()

def is_youtube_url(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower()

    return (
        host == "youtu.be"
        or host.endswith(".youtu.be")
        or "youtube." in host
        or "youtube-nocookie.com" in host
    )

def get_cili_token(cili_url: str, user: str, password: str) -> str:
    resp = _with_retry(
        lambda: requests.post(
            f"{cili_url}/api/auth/login",
            json={"username": user, "password": password},
            timeout=15,
        ),
        label="Login",
    )
    return resp.json()["accessToken"]


def _validate_target(folder_id: int | None, testimonial_id: int | None) -> None:
    """Genau eines von beidem muss gesetzt sein — spiegelt die serverseitige
    Regel in UploadService.initUpload() (de.toengi.cili.service.UploadService)."""
    if (folder_id is None) == (testimonial_id is None):
        raise ValueError("Entweder folder_id oder testimonial_id muss gesetzt sein")


def upload_file(filepath: Path, *, token: str, cili_url: str,
                folder_id: int | None = None, testimonial_id: int | None = None,
                chunk_size: int = CHUNK_SIZE, pin_to_top: bool = False) -> dict:
    """Lädt eine lokale Datei über die CILI Chunked-Upload-API hoch — entweder
    in einen Ordner (folder_id) oder als Testimonial-Anhang (testimonial_id)."""
    _validate_target(folder_id, testimonial_id)
    if not (_MIN_CHUNK_SIZE <= chunk_size <= _MAX_CHUNK_SIZE):
        raise ValueError(
            f"chunk_size {chunk_size // 1024 // 1024} MB außerhalb des erlaubten Bereichs "
            f"({_MIN_CHUNK_SIZE // 1024 // 1024}–{_MAX_CHUNK_SIZE // 1024 // 1024} MB)"
        )

    filename  = filepath.name
    mime_type = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    file_size = filepath.stat().st_size
    headers   = {"Authorization": f"Bearer {token}"}

    # 1. Init
    init = _with_retry(
        lambda: requests.post(
            f"{cili_url}/api/uploads/init",
            json={
                "fileName":  filename,
                "mimeType":  mime_type,
                "totalSize": file_size,
                "chunkSize": chunk_size,
                **({"folderId": folder_id} if folder_id is not None else {}),
                **({"testimonialId": testimonial_id} if testimonial_id is not None else {}),
            },
            headers=headers,
            timeout=30,
        ),
        label="Upload-Init",
    )
    job_id = init.json()["jobId"]
    total_chunks = -(-file_size // chunk_size)  # ceiling division

    # 2. Chunks (mit Retry pro Chunk)
    with filepath.open("rb") as fh:
        idx = 0
        while chunk_data := fh.read(chunk_size):
            _with_retry(
                lambda d=chunk_data, i=idx: requests.put(
                    f"{cili_url}/api/uploads/{job_id}/chunk/{i}",
                    data=d,
                    headers={**headers, "Content-Type": "application/octet-stream"},
                    timeout=120,
                ),
                label=f"Chunk {idx + 1}/{total_chunks}",
            )
            idx += 1

    # 3. Abschließen
    complete_url = f"{cili_url}/api/uploads/{job_id}/complete"
    if pin_to_top:
        complete_url += "?pinToTop=true"
    done = _with_retry(
        lambda: requests.post(complete_url, headers=headers, timeout=30),
        label="Upload-Complete",
    )
    return done.json()


# yt-dlp-Postprocessor-Namen → menschenlesbare deutsche Labels
_PP_LABELS = {
    "FFmpegMergerPP":         "Video + Audio zusammenführen",
    "FFmpegVideoConvertorPP": "Format konvertieren",
    "FFmpegFixupM4aPP":       "Audio-Format korrigieren",
    "FFmpegFixupM3u8PP":      "Stream-Format korrigieren",
    "FFmpegEmbedSubtitlePP":  "Untertitel einbetten",
}


def _pp_progress_hook(d: dict) -> None:
    """Gibt FFmpeg-Postprocessing-Fortschritt von yt-dlp lesbar aus."""
    label = _PP_LABELS.get(d.get("postprocessor", ""), d.get("postprocessor", ""))
    status = d.get("status")

    if status == "started":
        print(f"FFmpeg: {label}...", flush=True)

    elif status == "finished":
        elapsed = d.get("elapsed")
        if elapsed:
            print(f"FFmpeg: {label} fertig ({elapsed:.1f}s)", flush=True)
        else:
            print(f"FFmpeg: {label} fertig", flush=True)
    elif status == "error":
        print(f"FFmpeg: {label} fehlgeschlagen", flush=True)


def _locate_downloaded_file(ydl, info: dict, tmp_dir: Path) -> Path:
    """Ermittelt die von yt-dlp geschriebene Datei (mit Glob-Fallback)."""
    tmp_file = Path(ydl.prepare_filename(info))
    if tmp_file.exists():
        return tmp_file
    candidates = list(tmp_dir.glob("*.mp4")) + list(tmp_dir.glob("*"))
    if not candidates:
        raise FileNotFoundError("Heruntergeladene Datei nicht gefunden")
    return candidates[0]


def _resolve_title(title: str | None, info: dict, title_fallback: str | None, url: str) -> str:
    """Titel bestimmen — Priorität: Parameter > Metadaten > Fallback > URL-Basename."""
    if title:
        return sanitize_filename(title)
    meta_title = (info.get("title") or "").strip()
    if meta_title:
        return sanitize_filename(meta_title)
    if title_fallback:
        return sanitize_filename(title_fallback)
    return Path(url.split("?")[0]).stem


def download_and_upload(
    url: str,
    folder_id: int,
    token: str,
    cili_url: str,
    *,
    title: str | None = None,
    title_fallback: str | None = None,
    max_height: int = 720,
    chunk_size: int = CHUNK_SIZE,
    password: str | None = None,
    cookies_file: str | None = None,
    pin_to_top: bool = False,
) -> dict:
    """
    Lädt ein Video per yt-dlp herunter und spielt es in einen CILI-Ordner ein.

    Parameters
    ----------
    url            : Video-URL (Loom, YouTube, Vimeo, …)
    folder_id      : CILI-Ordner-ID
    token          : gültiges CILI-Zugriffstoken
    cili_url       : Basis-URL der CILI-Instanz
    title          : Erzwungener Titel (überschreibt Metadaten und Fallback).
    title_fallback : Titel wenn Metadaten keinen Titel liefern (z.B. "Lifestyle Webinar 2026-06-24").
                     None → URL-Basename als letzter Fallback.
    max_height     : Maximale Videohöhe in Pixeln (Standard 720)
    chunk_size   : Upload-Chunk-Größe in Bytes
    cookies_file : Pfad zur Netscape-Cookie-Datei (für YouTube-Authentifizierung)

    Returns
    -------
    dict  CILI-Resource-Objekt des hochgeladenen Videos
    """
    try:
        import yt_dlp
    except ImportError:
        raise RuntimeError("yt-dlp nicht installiert — bitte: pip install yt-dlp")

    tmp_dir = Path(tempfile.mkdtemp(prefix="cili_video_"))
    try:
        print(f"  Download: {url}")
        base_opts = {
            "outtmpl": str(tmp_dir / "%(id)s.%(ext)s"),
            "format": f"bv*[height<={max_height}]+ba/b[height<={max_height}]/b",
            # Möglichst MP4 erzeugen
            "merge_output_format": "mp4",
            # Robuster gegen Netzwerkprobleme
            "retries": 10,
            "fragment_retries": 10,
            "extractor_retries": 5,
            "quiet": True,
            "no_warnings": True,
            "postprocessor_hooks": [_pp_progress_hook],
            # JS-Challenge-Solver aktivieren, wenn Deno vorhanden ist → volle Formate
            # auch mit Cookies / Web-Client. Ohne Deno werden die Optionen weggelassen.
            **({"js_runtimes": {"deno": {"path": _DENO_PATH}},
                "remote_components": ["ejs:github"]} if _DENO_PATH else {}),
            **({"videopassword": password} if password else {}),
        }

        # YouTube-Downloadstrategie (Stand yt-dlp 2026.x):
        #  * Cookies aktivieren YouTubes SABR-only-Experiment und lassen die Clients
        #    android/ios (kein Cookie-Support) aus -> es bleiben nur Storyboards uebrig.
        #    Cookies werden fuer YouTube daher NICHT im Normalfall verwendet.
        #  * Ohne Cookies liefern die Default-Clients via JS-Runtime (Node.js/Deno) die
        #    vollen DASH-Formate bis 1080p (brauchen po_token, selten HTTP 403).
        #  * Der android-Client liefert den vorgemergten Stream (format 18, 360p), der
        #    ohne po_token zuverlaessig laedt -> robuster Fallback.
        android_fallback = {
            **base_opts,
            "format": f"best[height<={max_height}]/best",
            "extractor_args": {"youtube": {"player_client": ["android"]}},
        }
        if is_youtube_url(url):
            attempts = [
                ("Default-Clients (beste Qualitaet)", base_opts),
                ("android pre-merged (robust)", android_fallback),
            ]
            if cookies_file:
                attempts.append(
                    ("mit Cookies (private/altersbeschraenkte Videos)",
                     {**base_opts, "cookiefile": cookies_file}))
        else:
            # Loom, Vimeo, ...: kein SABR-Problem; Cookies direkt nutzen falls vorhanden.
            attempts = [("Standard",
                         {**base_opts,
                          **({"cookiefile": cookies_file} if cookies_file else {})})]

        info = None
        tmp_file = None
        last_exc: Exception | None = None
        for i, (label, opts) in enumerate(attempts, 1):
            try:
                if i > 1:
                    print(f"  Fallback-Versuch {i}: {label}", flush=True)
                with yt_dlp.YoutubeDL(opts) as ydl:
                    info     = ydl.extract_info(url, download=True)
                    tmp_file = _locate_downloaded_file(ydl, info, tmp_dir)
                last_exc = None
                break
            except Exception as exc:
                last_exc = exc
                print(f"  Versuch {i} ({label}) fehlgeschlagen: {exc.__class__.__name__}",
                      flush=True)
        if last_exc is not None:
            raise last_exc

        resolved_title = _resolve_title(title, info, title_fallback, url)
        upload_name = f"{resolved_title}{tmp_file.suffix}"
        final_file  = tmp_dir / upload_name
        tmp_file.rename(final_file)

        size_mb = final_file.stat().st_size // 1024 // 1024
        print(f"  Titel:  {resolved_title}")
        print(f"  Upload: {upload_name} ({size_mb} MB) → Ordner {folder_id}")

        result = upload_file(final_file, token=token, cili_url=cili_url, folder_id=folder_id,
                              chunk_size=chunk_size, pin_to_top=pin_to_top)
        print(f"  ✓ Hochgeladen (Resource-ID {result.get('id', '?')})")
        return result

    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


# ── CLI ───────────────────────────────────────────────────────────────────────

def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Video-Download und Upload in einen CILI-Ordner.",
        epilog=(
            "Beispiele:\n"
            "  python video_upload.py https://youtu.be/xyz --folder-id 7\n"
            "  python video_upload.py https://www.loom.com/share/abc --folder-id 42 --title 'Webinar Juni'"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("url", help="Video-URL (YouTube, Loom, Vimeo, …)")
    parser.add_argument("--folder-id", required=True, type=int, metavar="ID",
                        help="CILI-Ordner-ID (Pflicht)")
    parser.add_argument("--title", metavar="TEXT",
                        help="Titel/Dateiname (Standard: aus Video-Metadaten)")
    parser.add_argument("--max-height", type=int, default=720, metavar="PX",
                        help="Maximale Auflösung beim Download in Pixeln (Standard: 720)")
    parser.add_argument("--chunk-size", type=int, default=CHUNK_SIZE // (1024 * 1024),
                        metavar="MB", help="Upload-Chunk-Größe in MB (Standard: 5, Min: 1, Max: 100)")
    parser.add_argument("--password", metavar="PW",
                        help="Video-Passwort (für passwortgeschützte Videos)")
    parser.add_argument("--cookies-file", metavar="PFAD",
                        default=os.getenv("YTDLP_COOKIES_FILE"),
                        help="Pfad zur Netscape-Cookies-Datei")
    parser.add_argument("--env", metavar="PFAD",
                        help="Pfad zur .env-Datei (Standard: .env im Arbeitsverzeichnis)")
    return parser.parse_args()


def main() -> None:
    args = _parse_args()
    _load_env(args.env)
    cili_url, user, pw = _cili_config()

    chunk_size_bytes = args.chunk_size * 1024 * 1024
    if not (_MIN_CHUNK_SIZE <= chunk_size_bytes <= _MAX_CHUNK_SIZE):
        sys.exit(
            f"Fehler: --chunk-size {args.chunk_size} MB außerhalb des erlaubten Bereichs "
            f"({_MIN_CHUNK_SIZE // 1024 // 1024}–{_MAX_CHUNK_SIZE // 1024 // 1024} MB)"
        )

    token = _cili_token_from_env()
    if token:
        print("CILI-Token aus Umgebung übernommen.")
    else:
        print("CILI-Login …")
        token = get_cili_token(cili_url, user, pw)

    download_and_upload(
        url                  = args.url,
        folder_id            = args.folder_id,
        token                = token,
        cili_url             = cili_url,
        title                = args.title,
        max_height           = args.max_height,
        chunk_size           = chunk_size_bytes,
        password     = args.password,
        cookies_file = args.cookies_file,
    )


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as exc:
        msg = str(exc)
        if msg.upper().startswith("ERROR: "):
            msg = msg[7:]
        sys.exit(f"Fehler: {msg}")
