#!/usr/bin/env python3
"""
Telegram → CILI Erfahrungsberichte-Importer (Mensch)

Einrichtung:
  1. pip install telethon requests python-dotenv ftfy faster-whisper
  2. Telegram API-Zugangsdaten holen: https://my.telegram.org/apps
  3. .env-Datei anlegen (Vorlage: scripts/telegram_import_mensch.env.example)
  4. Beim ersten Start: Telefonnummer + Code eingeben (Session wird danach gespeichert
     und von telegram_import_tier.py / telegram_import_webinare.py mitgenutzt)

Verwendung:
  python scripts/telegram_import_mensch.py
      Importiert alle Nachrichten, die neuer sind als der letzte State-Datei-Eintrag.

  python scripts/telegram_import_mensch.py --from-date 2026-06-01
      Nützlich beim allerersten Import: Startdatum manuell setzen.

  python scripts/telegram_import_mensch.py --env scripts/telegram_import_menschen.env
      Explizite .env-Datei angeben (Standard: .env im aktuellen Verzeichnis).
"""

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import telegram_common as common
from telegram_testimonial import TestimonialRunConfig, run

SOURCE_NAME = "Mensch"

# Bewusst NICHT pro Quelle aufgeteilt — alle Telegram-Quellen nutzen denselben Account
# und damit dieselbe Telethon-Session. Zwei Prozesse dürfen diese Datei nie gleichzeitig
# öffnen (SQLite "database is locked"), daher serialisiert AsyncConfig.telegramExecutor()
# (maxPoolSize=1) alle drei Import-Skripte java-seitig auf einen Prozess zur selben Zeit.
SESSION_FILE = Path(__file__).parent / "telegram_session"
STATE_FILE = Path(__file__).parent / f"telegram_import_{SOURCE_NAME}.state"


def _load_config(args) -> TestimonialRunConfig:
    return TestimonialRunConfig(
        tg_api_id=int(os.getenv("TG_API_ID", "0")),
        tg_api_hash=os.getenv("TG_API_HASH", ""),
        tg_phone=os.getenv("TG_PHONE", ""),
        tg_group=os.getenv("TG_GROUP", ""),
        session_file=SESSION_FILE,
        source_name=SOURCE_NAME,
        is_human=True,
        is_animal=False,
        ai_url=os.getenv("AI_URL", "http://localhost:11434"),
        ai_model=os.getenv("AI_MODEL", "llama3"),
        ai_timeout=int(os.getenv("AI_TIMEOUT", "180")),
        ai_num_ctx=int(os.getenv("AI_NUM_CTX", "4096")),
        ai_num_predict=int(os.getenv("AI_NUM_PREDICT", "1024")),
        ai_retry_backoff_seconds=float(os.getenv("AI_RETRY_BACKOFF_SECONDS", "15")),
        min_confidence=float(os.getenv("MIN_CONFIDENCE", "0.75")),
        cili_url=os.getenv("CILI_URL", "http://localhost:8080"),
        cili_user=os.getenv("CILI_USER", "admin"),
        cili_pass=os.getenv("CILI_PASS", "admin"),
        whisper_model=os.getenv("WHISPER_MODEL", "medium"),
        whisper_device=os.getenv("WHISPER_DEVICE", "cuda"),
        whisper_compute_type=os.getenv("WHISPER_COMPUTE_TYPE", "int8_float16"),
        whisper_lang=os.getenv("WHISPER_LANG", "auto"),
        max_messages_per_run=int(os.getenv("MAX_MESSAGES_PER_RUN", "50")),
        max_runtime_minutes=float(os.getenv("MAX_RUNTIME_MINUTES", "45")),
        state_file=STATE_FILE,
        manual_cutoff=common.parse_manual_cutoff(args.from_date),
    )


def _validate_config(cfg: TestimonialRunConfig) -> None:
    if cfg.tg_api_id == 0 or not cfg.tg_api_hash:
        sys.exit(
            "Fehler: TG_API_ID und TG_API_HASH müssen gesetzt sein.\n"
            "Zugangsdaten unter https://my.telegram.org/apps erstellen."
        )
    if not cfg.tg_group:
        sys.exit("Fehler: TG_GROUP muss gesetzt sein (Gruppenname, -link oder numerische ID).")


def main() -> None:
    args = common.parse_args("Telegram → CILI Erfahrungsberichte-Importer (Mensch)")
    common.load_env(args.env)
    cfg = _load_config(args)
    _validate_config(cfg)
    run(cfg)


if __name__ == "__main__":
    main()
