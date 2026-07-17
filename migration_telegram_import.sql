-- Migration: Telegram-Import in Prozessliste integrieren
-- Einmalig auf dem Produktionsserver ausführen.
-- Voraussetzung: cili_schema.sql wurde bereits eingespielt (Basistabellen vorhanden).

-- 1. resource_id nullable machen (Systemjobs wie Telegram-Import haben keine Resource)
ALTER TABLE processing_jobs
  MODIFY COLUMN resource_id BIGINT DEFAULT NULL;

-- 2. TELEGRAM_IMPORT + VIDEO_URL_IMPORT zum Enum hinzufügen
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
    'VIDEO_URL_IMPORT'
  ) COLLATE utf8mb4_unicode_ci NOT NULL;
