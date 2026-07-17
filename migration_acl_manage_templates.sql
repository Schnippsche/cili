-- migration_acl_manage_templates.sql
-- Neues ACL-Recht MANAGE_TEMPLATES (Sammlung als Vorlage markieren) sowie
-- neuer Resource-Type COLLECTIONS (global-scoped, analog zu TESTIMONIALS).
ALTER TABLE acl_entries
  MODIFY COLUMN resource_type enum ('FOLDER','RESOURCE','GLOBAL','TESTIMONIALS','COLLECTIONS') COLLATE utf8mb4_unicode_ci NOT NULL,
  MODIFY COLUMN permission enum ('READ','WRITE','DELETE','DOWNLOAD','UPLOAD','SHARE','MANAGE_METADATA','MANAGE_SUBTITLES','TRANSLATE_SUBTITLES','ADMIN','MANAGE_TEMPLATES') COLLATE utf8mb4_unicode_ci NOT NULL;
