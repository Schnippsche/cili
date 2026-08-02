"""
Unit tests for telegram_common.py — reine Hilfsfunktionen (State-Datei, Medien-
Klassifikation, Webinar-Erkennung). Telegram-Client-/Netzwerk-Funktionen sind
laut Spec (docs/superpowers/specs/2026-08-02-telegram-script-split-design.md)
nicht automatisiert testbar; siehe dortige manuelle Testschritte.
Run: python -m pytest scripts/test_telegram_common.py -v
"""

import os
import sys
from datetime import datetime
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(__file__))
from telegram_common import (
    classify_media, is_webinar, extract_video_url,
    load_state_date, save_state_date, resolve_cutoff,
)


def _msg(photo=None, video=None, video_note=None, audio=None, gif=None, sticker=None):
    return SimpleNamespace(photo=photo, video=video, video_note=video_note, audio=audio,
                            gif=gif, sticker=sticker)


def test_classify_media_photo():
    assert classify_media(_msg(photo=True)) == "image"


def test_classify_media_video():
    assert classify_media(_msg(video=True)) == "video"


def test_classify_media_audio():
    assert classify_media(_msg(audio=True)) == "audio"


def test_classify_media_none_for_other_types():
    assert classify_media(_msg()) is None


def test_classify_media_photo_takes_priority_over_video():
    assert classify_media(_msg(photo=True, video=True)) == "image"


def test_classify_media_round_video_note_excluded():
    assert classify_media(_msg(video=True, video_note=True)) is None


def test_classify_media_gif_excluded():
    assert classify_media(_msg(video=True, gif=True)) is None


def test_classify_media_video_sticker_excluded():
    assert classify_media(_msg(video=True, sticker=True)) is None


def test_is_webinar_matches_hashtag_variant():
    assert is_webinar("Heute: Lifestyle #Webinar um 19 Uhr") is True


def test_is_webinar_matches_plain_variant_case_insensitive():
    assert is_webinar("LIFESTYLE WEBINAR heute Abend") is True


def test_is_webinar_false_for_unrelated_text():
    assert is_webinar("Mein Erfahrungsbericht zu Produkt X") is False


def test_extract_video_url_finds_first_url():
    text = "Schau hier: https://www.loom.com/share/abc123 und teile es"
    assert extract_video_url(text) == "https://www.loom.com/share/abc123"


def test_extract_video_url_strips_trailing_punctuation():
    text = "Link: https://youtu.be/xyz."
    assert extract_video_url(text) == "https://youtu.be/xyz"


def test_extract_video_url_none_when_no_url():
    assert extract_video_url("kein Link hier") is None


def test_state_date_roundtrip(tmp_path):
    state_file = tmp_path / "test.state"
    dt = datetime(2026, 6, 1, 12, 30, 0)
    save_state_date(state_file, dt)
    assert load_state_date(state_file) == dt


def test_load_state_date_missing_file_returns_none(tmp_path):
    assert load_state_date(tmp_path / "missing.state") is None


def test_load_state_date_corrupt_file_returns_none(tmp_path):
    state_file = tmp_path / "corrupt.state"
    state_file.write_text("not-a-date")
    assert load_state_date(state_file) is None


def test_resolve_cutoff_manual_overrides_state(tmp_path):
    state_file = tmp_path / "test.state"
    save_state_date(state_file, datetime(2026, 1, 1))
    manual = datetime(2026, 6, 1)
    assert resolve_cutoff(state_file, manual) == manual


def test_resolve_cutoff_falls_back_to_state_file(tmp_path):
    state_file = tmp_path / "test.state"
    dt = datetime(2026, 5, 1)
    save_state_date(state_file, dt)
    assert resolve_cutoff(state_file, None) == dt


def test_resolve_cutoff_none_when_nothing_available(tmp_path):
    assert resolve_cutoff(tmp_path / "missing.state", None) is None
