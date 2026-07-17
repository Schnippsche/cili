-- migration_collections_templates.sql
ALTER TABLE collection
  ADD COLUMN is_template BOOLEAN NOT NULL DEFAULT FALSE;
