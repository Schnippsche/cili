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


def _msg(photo=None, video=None, video_note=None, audio=None, gif=None, sticker=None):
    return SimpleNamespace(photo=photo, video=video, video_note=video_note, audio=audio,
                            gif=gif, sticker=sticker)


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


def test_classify_media_gif_excluded():
    # GIFs tragen DocumentAttributeAnimated zusätzlich zu DocumentAttributeVideo
    # und sind daher ebenfalls msg.video-truthy — msg.gif muss das ausschließen.
    assert _classify_media(_msg(video=True, gif=True)) is None


def test_classify_media_video_sticker_excluded():
    # Webm-Video-Sticker tragen DocumentAttributeSticker zusätzlich zu
    # DocumentAttributeVideo — msg.sticker muss das ausschließen.
    assert _classify_media(_msg(video=True, sticker=True)) is None


class _FakeSegment:
    def __init__(self, text):
        self.text = text


def test_transcribe_for_classification_joins_segment_texts(monkeypatch):
    class FakeModel:
        def transcribe(self, path, language=None, beam_size=1, vad_filter=True):
            return [_FakeSegment(" Hallo "), _FakeSegment("Welt ")], None

    monkeypatch.setattr(telegram_import, "_whisper_model", FakeModel())
    result = telegram_import.transcribe_for_classification(Path("dummy.mp4"))
    assert result == "Hallo Welt"


def test_transcribe_for_classification_returns_none_when_no_segments(monkeypatch):
    class FakeModel:
        def transcribe(self, path, language=None, beam_size=1, vad_filter=True):
            return [], None

    monkeypatch.setattr(telegram_import, "_whisper_model", FakeModel())
    assert telegram_import.transcribe_for_classification(Path("dummy.mp4")) is None


def test_transcribe_for_classification_returns_none_on_exception(monkeypatch):
    class FakeModel:
        def transcribe(self, *args, **kwargs):
            raise RuntimeError("boom")

    monkeypatch.setattr(telegram_import, "_whisper_model", FakeModel())
    assert telegram_import.transcribe_for_classification(Path("dummy.mp4")) is None
