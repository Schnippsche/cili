-- Migration: Testimonial-KI-Textvorschlag (TESTIMONIAL_SUMMARY-Job-Typ)
-- Einmalig auf dem Produktionsserver ausführen.
-- Voraussetzung: cili_schema.sql wurde bereits eingespielt (Basistabellen vorhanden).

ALTER TABLE processing_jobs
  MODIFY COLUMN type ENUM(
    'THUMBNAIL',
    'OCR',
    'PREVIEW',
    'TRANSCODE',
    'VIDEO_ANALYSIS',
    'VIDEO_TRANSCODE',
    'WAV_EXTRACT',
    'WHISPER_TRANSCRIBE',
    'AUDIO_NORMALIZE',
    'SUBTITLE_TRANSLATE',
    'DOCUMENT_TRANSLATE',
    'OLLAMA_ANALYSIS',
    'TELEGRAM_IMPORT',
    'VIDEO_URL_IMPORT',
    'VIDEO_CLIP',
    'TESTIMONIAL_SUMMARY'
  ) COLLATE utf8mb4_unicode_ci NOT NULL;
