#!/usr/bin/env python3
"""
Telegram → CILI Lifestyle-Webinar-Importer

Eigenständiger, wöchentlicher Lauf: lädt im Text verlinkte Lifestyle-Webinar-
Videos aus der Telegram-Gruppe herunter und lädt sie in den konfigurierten
CILI-Ordner hoch. Postet KEINE Erfahrungsberichte — siehe telegram_import_mensch.py
für die Testimonial-Pipeline derselben Gruppe.

Einrichtung: siehe scripts/telegram_import_webinare.env.example.

Verwendung:
  python scripts/telegram_import_webinare.py
  python scripts/telegram_import_webinare.py --from-date 2026-06-01
  python scripts/telegram_import_webinare.py --env scripts/telegram_import_webinare.env
"""

import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import telegram_common as common
from video_upload import download_and_upload

SOURCE_NAME = "Webinar"
SESSION_FILE = Path(__file__).parent / "telegram_session"
STATE_FILE = Path(__file__).parent / f"telegram_import_{SOURCE_NAME}.state"


@dataclass
class WebinarRunConfig:
    tg_api_id: int
    tg_api_hash: str
    tg_phone: str
    tg_group: str
    cili_url: str
    cili_user: str
    cili_pass: str
    webinar_folder_id: int
    webinar_max_height: int
    skip_webinars: bool
    max_messages_per_run: int
    max_runtime_minutes: float
    state_file: Path
    session_file: Path
    manual_cutoff: datetime | None = None


def _load_config(args) -> WebinarRunConfig:
    webinar_folder_id = os.getenv("WEBINAR_FOLDER_ID", "")
    if not webinar_folder_id:
        sys.exit("Fehler: WEBINAR_FOLDER_ID muss gesetzt sein (CILI-Ordner-ID für Webinar-Uploads).")
    return WebinarRunConfig(
        tg_api_id=int(os.getenv("TG_API_ID", "0")),
        tg_api_hash=os.getenv("TG_API_HASH", ""),
        tg_phone=os.getenv("TG_PHONE", ""),
        tg_group=os.getenv("TG_GROUP", ""),
        cili_url=os.getenv("CILI_URL", "http://localhost:8080"),
        cili_user=os.getenv("CILI_USER", "admin"),
        cili_pass=os.getenv("CILI_PASS", "admin"),
        webinar_folder_id=int(webinar_folder_id),
        webinar_max_height=int(os.getenv("WEBINAR_MAX_HEIGHT", "720")),
        skip_webinars=os.getenv("SKIP_WEBINARS", "false").lower() == "true",
        max_messages_per_run=int(os.getenv("MAX_MESSAGES_PER_RUN", "50")),
        max_runtime_minutes=float(os.getenv("MAX_RUNTIME_MINUTES", "45")),
        state_file=STATE_FILE,
        session_file=SESSION_FILE,
        manual_cutoff=common.parse_manual_cutoff(args.from_date),
    )


def _validate_config(cfg: WebinarRunConfig) -> None:
    if cfg.tg_api_id == 0 or not cfg.tg_api_hash:
        sys.exit(
            "Fehler: TG_API_ID und TG_API_HASH müssen gesetzt sein.\n"
            "Zugangsdaten unter https://my.telegram.org/apps erstellen."
        )
    if not cfg.tg_group:
        sys.exit("Fehler: TG_GROUP muss gesetzt sein (Gruppenname, -link oder numerische ID).")


def _get_cili_token(cfg: WebinarRunConfig) -> str:
    return common.get_cili_token(cfg.cili_url, cfg.cili_user, cfg.cili_pass,
                                  env_token=os.getenv("CILI_TOKEN"))


def _try_import_webinar(cfg: WebinarRunConfig, text: str, msg_utc: datetime) -> str:
    """Lädt das im Text verlinkte Webinar-Video hoch. Liefert 'imported', 'skipped' oder 'failed'.

    Holt unmittelbar vor dem Upload ein frisches Token: Video-Downloads dauern oft
    länger als kurzlebige Tokens, ein zu Laufbeginn geholtes Token wäre beim
    tatsächlichen Upload sonst bereits abgelaufen (401)."""
    if cfg.skip_webinars:
        print("  Webinar-Video übersprungen (SKIP_WEBINARS=true)")
        return "skipped"
    video_url = common.extract_video_url(text)
    if not video_url:
        print("  Keine Video-URL im Text gefunden — übersprungen")
        return "skipped"
    try:
        upload_token = _get_cili_token(cfg)
        download_and_upload(
            url=video_url,
            folder_id=cfg.webinar_folder_id,
            token=upload_token,
            cili_url=cfg.cili_url,
            title_fallback=f"Lifestyle Webinar {msg_utc.strftime('%Y-%m-%d')}",
            max_height=cfg.webinar_max_height,
            pin_to_top=True,
        )
        return "imported"
    except Exception as exc:
        print(f"  Webinar-Video fehlgeschlagen: {exc}")
        return "failed"


async def _process_message(cfg: WebinarRunConfig, message, album_map: dict,
                           processed_group_ids: set) -> str:
    """Liefert 'imported', 'skipped', 'failed' oder 'album_dup'."""
    if message.grouped_id:
        if message.grouped_id in processed_group_ids:
            return "album_dup"
        processed_group_ids.add(message.grouped_id)
        group_msgs = album_map[message.grouped_id]
        text = next((m.text for m in group_msgs if m.text), "").strip()
    else:
        text = (message.text or "").strip()

    if not text or not common.is_webinar(text):
        return "skipped"

    msg_utc = common.to_utc_naive(message.date)
    print(f"[{msg_utc.strftime('%d.%m.%Y %H:%M')}] Webinar-Nachricht erkannt")
    return _try_import_webinar(cfg, text, msg_utc)


async def _run_async(cfg: WebinarRunConfig) -> None:
    client = common.build_client(cfg.session_file, cfg.tg_api_id, cfg.tg_api_hash)
    async with client:
        await client.start(phone=cfg.tg_phone or (lambda: input("Telefonnummer: ")))

        group = await common.resolve_group(client, cfg.tg_group)
        print(f"Gruppe: {getattr(group, 'title', cfg.tg_group)}")

        cutoff = common.resolve_cutoff(cfg.state_file, cfg.manual_cutoff)
        print()

        new_messages = await common.fetch_new_messages(client, group, cutoff)
        if not new_messages:
            print("Keine neuen Nachrichten seit dem letzten Import.")
            return

        print(f"{len(new_messages)} neue Nachricht(en) gefunden — verarbeite älteste zuerst.\n")

        album_map = common.group_albums(new_messages)
        processed_group_ids: set = set()
        counts = {"imported": 0, "skipped": 0, "failed": 0}
        run_started = time.monotonic()
        processed = 0
        limit_hit = None

        for message in reversed(new_messages):
            if processed >= cfg.max_messages_per_run:
                limit_hit = f"{cfg.max_messages_per_run} Nachrichten"
                break
            if (time.monotonic() - run_started) / 60 >= cfg.max_runtime_minutes:
                limit_hit = f"{cfg.max_runtime_minutes:.0f} Minuten Laufzeit"
                break

            status = await _process_message(cfg, message, album_map, processed_group_ids)
            processed += 1
            if status in counts:
                counts[status] += 1
            common.save_state_date(cfg.state_file, common.to_utc_naive(message.date))

        if limit_hit:
            print(f"Limit erreicht ({limit_hit}) — {len(new_messages) - processed} "
                  f"verbleibende Nachricht(en) folgen im nächsten Lauf.\n")

        print(f"Fertig: {counts['imported']} importiert, {counts['skipped']} übersprungen, "
              f"{counts['failed']} fehlgeschlagen.")


def main() -> None:
    args = common.parse_args("Telegram → CILI Lifestyle-Webinar-Importer")
    common.load_env(args.env)
    cfg = _load_config(args)
    _validate_config(cfg)
    import asyncio
    asyncio.run(_run_async(cfg))


if __name__ == "__main__":
    main()
