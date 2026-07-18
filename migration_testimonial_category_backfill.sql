-- migration_testimonial_category_backfill.sql
-- Einmalige Datenkorrektur: Normalisiere alle Testimonial-Kategorie-Werte auf Singular.
-- Vor der Tiere-Gruppe gab es ausschließlich menschliche Erfahrungsberichte.
UPDATE testimonials SET source = 'Mensch' WHERE source IS NULL;

-- Bestehende Werte aus dem Telegram-Import (bisher Plural) auf Singular umstellen.
UPDATE testimonials SET source = 'Mensch' WHERE source = 'Menschen';
UPDATE testimonials SET source = 'Tier'   WHERE source = 'Tiere';
