-- Migration: source-Spalte für Multi-Source-Telegram-Import
-- Einmalig auf dem Produktionsserver ausführen.
-- Voraussetzung: cili_schema.sql wurde bereits eingespielt (Basistabellen vorhanden).

ALTER TABLE testimonials
  ADD COLUMN source VARCHAR(100) NULL AFTER tags;

ALTER TABLE processing_jobs
  ADD COLUMN source VARCHAR(100) NULL AFTER type;
