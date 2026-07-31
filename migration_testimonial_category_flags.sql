-- migration_testimonial_category_flags.sql
-- Produktiv-Migration: testimonials.source (String) -> is_human/is_animal (Boolean)
-- Vor dem Ausführen: Backup der Produktiv-DB nicht vergessen.
-- Defensiv gegenüber historischen Werten (falls migration_testimonial_category_backfill.sql
-- in dieser DB nie gelaufen ist): NULL, 'Menschen' (Plural), 'Tiere' (Plural) möglich.
--
-- WICHTIG — Wartungsfenster: Diese Tabelle hat einen immer-aktiven externen Schreiber
-- (scripts/telegram_import.py, nächtlicher Cron-Job je konfigurierter Quelle). Vor dem
-- Ausführen die CILI-Anwendung UND den Telegram-Import-Cron stoppen (bzw. sicherstellen,
-- dass beide für die Dauer der Migration keine neuen testimonials-Zeilen schreiben) —
-- sonst könnten Zeilen entstehen, die zwischen den beiden ALTER TABLE-Schritten mit
-- is_human=0/is_animal=0 angelegt werden und von den UPDATE-Schritten nicht mehr erfasst
-- werden (der finale CHECK-Constraint würde das zuverlässig als Fehler aufdecken, aber
-- erst am Ende — sauberer ist es, die Schreiblast vorher zu stoppen).
--
-- WICHTIG — Fortsetzen nach Abbruch: Dieses Skript ist NICHT gefahrlos erneut komplett
-- ausführbar, falls es mittendrin abbricht (z. B. Verbindungsabbruch). Vor einem erneuten
-- Lauf prüfen, wo es stehen geblieben ist:
--   SHOW COLUMNS FROM testimonials LIKE 'is_human';
--   SHOW COLUMNS FROM testimonials LIKE 'source';
-- - Beide Spalten (is_human/is_animal) fehlen noch      -> von vorne beginnen (erstes ALTER TABLE).
-- - is_human/is_animal existieren, source existiert noch -> erstes ALTER TABLE NICHT wiederholen
--   (sonst Fehler "Duplicate column"), stattdessen direkt mit den beiden UPDATE-Anweisungen
--   fortfahren, dann mit dem finalen ALTER TABLE.
-- - source existiert nicht mehr                          -> Migration ist bereits abgeschlossen.

-- Pre-Flight-Check (VOR dem unten stehenden ALTER TABLE separat ausführen und Ergebnis prüfen):
-- deckt einen unbekannten dritten source-Wert auf (Tippfehler, manuelle
-- DB-Bearbeitung), der von keiner der folgenden UPDATE-Klauseln erfasst würde
-- und sonst erst am finalen CHECK-Constraint mit einer rohen
-- Constraint-Verletzung auffiele.
--
--   SELECT id, source FROM testimonials
--   WHERE source IS NOT NULL AND source NOT IN ('Mensch', 'Menschen', 'Tier', 'Tiere');
--
-- Erwartung: 0 Zeilen. Falls Zeilen zurückkommen: NICHT fortfahren, sondern
-- die betroffenen Datensätze manuell untersuchen und den Wert vor der
-- Migration korrigieren (z. B. per gezieltem UPDATE auf 'Mensch'/'Tier').

ALTER TABLE `testimonials`
    ADD COLUMN `is_human`  TINYINT(1) NOT NULL DEFAULT 0 AFTER `text`,
    ADD COLUMN `is_animal` TINYINT(1) NOT NULL DEFAULT 0 AFTER `is_human`;

UPDATE `testimonials`
    SET `is_human` = 1
    WHERE `source` IN ('Mensch', 'Menschen') OR `source` IS NULL;

UPDATE `testimonials`
    SET `is_animal` = 1
    WHERE `source` IN ('Tier', 'Tiere');

ALTER TABLE `testimonials`
    DROP COLUMN `source`,
    ADD CONSTRAINT `chk_testimonials_category` CHECK (`is_human` = 1 OR `is_animal` = 1);

-- Nach lokalem Testlauf (dev-DB) verifizieren:
--
--   SELECT COUNT(*) FROM testimonials WHERE is_human = 0 AND is_animal = 0;
--   -- Erwartung: 0
