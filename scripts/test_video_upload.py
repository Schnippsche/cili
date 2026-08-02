"""
Unit tests for video_upload.py — nur die reine Validierungslogik von
upload_file() (kein echter Netzwerk-/Dateizugriff nötig).
Run: python -m pytest scripts/test_video_upload.py -v
"""

import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(__file__))
from video_upload import _validate_target, upload_file


def test_validate_target_raises_when_both_set():
    with pytest.raises(ValueError):
        _validate_target(folder_id=1, testimonial_id=2)


def test_validate_target_raises_when_neither_set():
    with pytest.raises(ValueError):
        _validate_target(folder_id=None, testimonial_id=None)


def test_validate_target_passes_with_only_folder_id():
    _validate_target(folder_id=1, testimonial_id=None)  # keine Exception


def test_validate_target_passes_with_only_testimonial_id():
    _validate_target(folder_id=None, testimonial_id=1)  # keine Exception


def test_upload_file_raises_before_any_network_call_when_target_invalid(tmp_path):
    f = tmp_path / "video.mp4"
    f.write_bytes(b"data")
    # Weder folder_id noch testimonial_id gesetzt und eine nicht auflösbare
    # URL: schlaegt die Validierung nicht VOR jedem Request fehl, würde dieser
    # Test an einem Verbindungsfehler statt ValueError scheitern.
    with pytest.raises(ValueError):
        upload_file(f, token="t", cili_url="http://cili-upload-file-test.invalid")
