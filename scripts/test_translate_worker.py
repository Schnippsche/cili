"""
Unit tests for translate_worker.py.
No real NLLB model required — translate_batch is mocked.
Run: python -m pytest scripts/test_translate_worker.py -v
"""

import sys
import os

# Allow importing translate_worker from the scripts/ directory
sys.path.insert(0, os.path.dirname(__file__))
from translate_worker import parse_vtt, write_vtt, NLLB_CODES, Cue


# ── NLLB_CODES ───────────────────────────────────────────────────────────────

def test_nllb_codes_contains_required_languages():
    required = {"de", "en", "fr", "es", "it", "pt", "ja", "zh", "pl"}
    missing = required - set(NLLB_CODES.keys())
    assert not missing, f"NLLB_CODES is missing required languages: {missing}"


def test_nllb_codes_all_have_script_suffix():
    for code, nllb in NLLB_CODES.items():
        assert "_" in nllb, f"NLLB code for '{code}' has no script suffix: {nllb!r}"


# ── parse_vtt ─────────────────────────────────────────────────────────────────

BASIC_VTT = """\
WEBVTT

1
00:00:01.000 --> 00:00:03.000
Hallo Welt

2
00:00:05.000 --> 00:00:07.500
Wie geht es dir?
"""


def test_parse_vtt_basic_count():
    cues = parse_vtt(BASIC_VTT)
    assert len(cues) == 2


def test_parse_vtt_timestamps_preserved():
    cues = parse_vtt(BASIC_VTT)
    assert cues[0].timestamps == "00:00:01.000 --> 00:00:03.000"
    assert cues[1].timestamps == "00:00:05.000 --> 00:00:07.500"


def test_parse_vtt_text_extracted():
    cues = parse_vtt(BASIC_VTT)
    assert cues[0].text == "Hallo Welt"
    assert cues[1].text == "Wie geht es dir?"


def test_parse_vtt_multiline_cue_joined():
    vtt = "WEBVTT\n\n1\n00:00:01.000 --> 00:00:02.000\nZeile eins\nZeile zwei\n"
    cues = parse_vtt(vtt)
    assert len(cues) == 1
    assert cues[0].text == "Zeile eins Zeile zwei"


def test_parse_vtt_no_cue_ids():
    vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nText ohne ID\n"
    cues = parse_vtt(vtt)
    assert len(cues) == 1
    assert cues[0].text == "Text ohne ID"


def test_parse_vtt_empty_returns_empty_list():
    assert parse_vtt("WEBVTT\n") == []
    assert parse_vtt("") == []


# ── write_vtt ─────────────────────────────────────────────────────────────────

def test_write_vtt_starts_with_webvtt():
    cues = [Cue("1", "00:00:01.000 --> 00:00:03.000", "Original")]
    result = write_vtt(cues, ["Translated"])
    assert result.startswith("WEBVTT")


def test_write_vtt_timestamps_preserved():
    cues = [Cue("1", "00:00:01.000 --> 00:00:03.000", "Original")]
    result = write_vtt(cues, ["Translated"])
    assert "00:00:01.000 --> 00:00:03.000" in result


def test_write_vtt_translated_text_used():
    cues = [Cue("1", "00:00:01.000 --> 00:00:03.000", "Hallo")]
    result = write_vtt(cues, ["Hello"])
    assert "Hello" in result
    assert "Hallo" not in result


def test_write_vtt_original_text_replaced():
    cues = parse_vtt(BASIC_VTT)
    result = write_vtt(cues, ["Hello World", "How are you?"])
    assert "Hallo Welt" not in result
    assert "Hello World" in result
    assert "How are you?" in result


def test_nllb_codes_values_match_spec():
    assert NLLB_CODES["de"] == "deu_Latn"
    assert NLLB_CODES["en"] == "eng_Latn"
    assert NLLB_CODES["ja"] == "jpn_Jpan"
    assert NLLB_CODES["zh"] == "zho_Hans"
    assert NLLB_CODES["pl"] == "pol_Latn"


def test_write_vtt_auto_increments_cue_ids():
    cues = [
        Cue("", "00:00:01.000 --> 00:00:02.000", "A"),
        Cue("", "00:00:02.000 --> 00:00:03.000", "B"),
    ]
    result = write_vtt(cues, ["X", "Y"])
    lines = result.splitlines()
    # Find cue IDs
    numeric_lines = [l for l in lines if l.strip().isdigit()]
    assert numeric_lines == ["1", "2"]
