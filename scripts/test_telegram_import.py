"""
Unit tests for telegram_import.py — nur reine Hilfsfunktionen. Download,
KI-Klassifikation und Chunked-Upload sind Telegram-/CILI-/GPU-abhängig und
laut Spec (docs/superpowers/specs/2026-08-02-telegram-video-audio-import-design.md)
nicht automatisiert testbar; siehe dortige manuelle Testschritte.
Run: python -m pytest scripts/test_telegram_import.py -v
"""

import os
import sys
from pathlib import Path
from types import SimpleNamespace

# telegram_import.py parst beim Modul-Import argparse-Argumente aus sys.argv
# (Modul-Level-Code, kein `if __name__ == "__main__"`-Schutz) — vor dem Import
# auf eine leere Argumentliste setzen, damit pytests eigene CLI-Argumente nicht
# als unbekannte Optionen durchschlagen.
sys.argv = ["telegram_import.py"]
sys.path.insert(0, os.path.dirname(__file__))
import telegram_import
from telegram_import import _classify_media


def _msg(photo=None, video=None, video_note=None, audio=None):
    return SimpleNamespace(photo=photo, video=video, video_note=video_note, audio=audio)


def test_classify_media_photo():
    assert _classify_media(_msg(photo=True)) == "image"


def test_classify_media_video():
    assert _classify_media(_msg(video=True)) == "video"


def test_classify_media_audio():
    assert _classify_media(_msg(audio=True)) == "audio"


def test_classify_media_none_for_other_types():
    assert _classify_media(_msg()) is None


def test_classify_media_photo_takes_priority_over_video():
    # Ein Album-Mitglied könnte theoretisch beides melden — Foto hat Vorrang,
    # weil _classify_media zuerst auf msg.photo prüft.
    assert _classify_media(_msg(photo=True, video=True)) == "image"


def test_classify_media_round_video_note_excluded():
    # Telethons msg.video ist AUCH bei runden Video-Notes wahr (keine eigene
    # Ausschluss-Bedingung, anders als bei audio/voice) — video_note muss
    # deshalb explizit geprüft werden.
    assert _classify_media(_msg(video=True, video_note=True)) is None
