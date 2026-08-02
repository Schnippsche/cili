"""
Unit tests for telegram_import_webinare.py — Entscheidungslogik von
_try_import_webinar (Video-Download/-Upload selbst ist laut Spec
docs/superpowers/specs/2026-08-02-telegram-script-split-design.md nicht
automatisiert testbar; siehe dortige manuelle Testschritte).
Run: python -m pytest scripts/test_telegram_import_webinare.py -v
"""

import os
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
import telegram_import_webinare as webinare


def _cfg(**overrides) -> webinare.WebinarRunConfig:
    defaults = dict(
        tg_api_id=1, tg_api_hash="hash", tg_phone="+491234567890", tg_group="-100123",
        cili_url="http://localhost:8080", cili_user="admin", cili_pass="admin",
        webinar_folder_id=15, webinar_max_height=720, skip_webinars=False,
        max_messages_per_run=20, max_runtime_minutes=45.0,
        state_file=Path("unused.state"), session_file=Path("unused_session"),
    )
    defaults.update(overrides)
    return webinare.WebinarRunConfig(**defaults)


def test_try_import_webinar_skipped_when_skip_flag_set():
    cfg = _cfg(skip_webinars=True)
    status = webinare._try_import_webinar(cfg, "Lifestyle Webinar https://youtu.be/xyz", datetime(2026, 6, 1))
    assert status == "skipped"


def test_try_import_webinar_skipped_when_no_url_in_text():
    cfg = _cfg()
    status = webinare._try_import_webinar(cfg, "Lifestyle Webinar heute Abend, kein Link", datetime(2026, 6, 1))
    assert status == "skipped"


def test_try_import_webinar_imported_on_successful_upload(monkeypatch):
    cfg = _cfg()
    monkeypatch.setattr(webinare, "_get_cili_token", lambda c: "token123")
    monkeypatch.setattr(webinare, "download_and_upload", lambda **kwargs: {"id": 42})

    status = webinare._try_import_webinar(cfg, "Lifestyle Webinar https://youtu.be/xyz", datetime(2026, 6, 1))
    assert status == "imported"


def test_try_import_webinar_failed_on_upload_exception(monkeypatch):
    cfg = _cfg()
    monkeypatch.setattr(webinare, "_get_cili_token", lambda c: "token123")

    def _boom(**kwargs):
        raise RuntimeError("Download fehlgeschlagen")

    monkeypatch.setattr(webinare, "download_and_upload", _boom)

    status = webinare._try_import_webinar(cfg, "Lifestyle Webinar https://youtu.be/xyz", datetime(2026, 6, 1))
    assert status == "failed"
