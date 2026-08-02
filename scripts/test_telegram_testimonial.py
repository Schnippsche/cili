"""
Unit tests for telegram_testimonial.py — reine Hilfsfunktionen. KI-Klassifikation
(Netzwerk), Whisper-Modell-Laden und CILI-Posting sind laut Spec
(docs/superpowers/specs/2026-08-02-telegram-script-split-design.md) nicht
automatisiert testbar; siehe dortige manuelle Testschritte.
Run: python -m pytest scripts/test_telegram_testimonial.py -v
"""

import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
import telegram_testimonial
from telegram_testimonial import shorten_name, post_process_result, fix_json_control_chars


def test_shorten_name_two_parts():
    assert shorten_name("Peter Geisler") == "Peter G."


def test_shorten_name_already_short_unchanged():
    assert shorten_name("Peter") == "Peter"


def test_shorten_name_single_letter_lastname_unchanged():
    # len(parts[-1]) > 1 ist False für einbuchstabige Nachnamen -> unveraendert
    assert shorten_name("Peter X") == "Peter X"


def test_post_process_result_shortens_author_name():
    result = post_process_result({"author_name": "Maria Schmidt", "text": "", "tags": ""})
    assert result["author_name"] == "Maria S."


def test_post_process_result_strips_hashtags_and_emoji_from_text():
    result = post_process_result({"text": "Tolles #Produkt \U0001F600 wirklich super", "tags": ""})
    assert result["text"] == "Tolles Produkt wirklich super"


def test_post_process_result_dedupes_tags_case_insensitive():
    result = post_process_result({"tags": "#COPD, copd, Husten"})
    assert result["tags"] == "COPD,Husten"


def test_post_process_result_empty_tags_stay_empty():
    result = post_process_result({"tags": ""})
    assert result["tags"] == ""


def test_fix_json_control_chars_escapes_literal_newline_in_string():
    broken = '{"text": "Zeile1\nZeile2"}'
    fixed = fix_json_control_chars(broken)
    assert fixed == '{"text": "Zeile1\\nZeile2"}'


def test_fix_json_control_chars_leaves_valid_json_untouched():
    valid = '{"text": "ohne Steuerzeichen"}'
    assert fix_json_control_chars(valid) == valid


class _FakeSegment:
    def __init__(self, text):
        self.text = text


def test_transcribe_for_classification_joins_segment_texts(monkeypatch):
    class FakeModel:
        def transcribe(self, path, language=None, beam_size=1, vad_filter=True):
            return [_FakeSegment(" Hallo "), _FakeSegment("Welt ")], None

    monkeypatch.setattr(telegram_testimonial, "_whisper_model", FakeModel())
    result = telegram_testimonial.transcribe_for_classification(
        Path("dummy.mp4"), "medium", "cpu", "int8", "auto")
    assert result == "Hallo Welt"


def test_transcribe_for_classification_returns_none_when_no_segments(monkeypatch):
    class FakeModel:
        def transcribe(self, path, language=None, beam_size=1, vad_filter=True):
            return [], None

    monkeypatch.setattr(telegram_testimonial, "_whisper_model", FakeModel())
    assert telegram_testimonial.transcribe_for_classification(
        Path("dummy.mp4"), "medium", "cpu", "int8", "auto") is None


def test_transcribe_for_classification_returns_none_on_exception(monkeypatch):
    class FakeModel:
        def transcribe(self, *args, **kwargs):
            raise RuntimeError("boom")

    monkeypatch.setattr(telegram_testimonial, "_whisper_model", FakeModel())
    assert telegram_testimonial.transcribe_for_classification(
        Path("dummy.mp4"), "medium", "cpu", "int8", "auto") is None
