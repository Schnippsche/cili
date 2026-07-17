-- migration_bulk_import.sql
CREATE TABLE IF NOT EXISTS `bulk_import_jobs`
(
    `id`               char(36)     NOT NULL,
    `admin_user_id`    bigint       NOT NULL,
    `target_folder_id` bigint       NOT NULL,
    `root_name`        varchar(255) NOT NULL,
    `files_total`      int          NOT NULL DEFAULT '0',
    `files_done`       int          NOT NULL DEFAULT '0',
    `files_skipped`    int          NOT NULL DEFAULT '0',
    `files_failed`     int          NOT NULL DEFAULT '0',
    `created_at`       datetime(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_bulk_import_jobs_admin` (`admin_user_id`),
    CONSTRAINT `fk_bulk_import_jobs_admin` FOREIGN KEY (`admin_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bulk_import_jobs_folder` FOREIGN KEY (`target_folder_id`) REFERENCES `folders` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bulk_import_items`
(
    `id`                 bigint        NOT NULL AUTO_INCREMENT,
    `bulk_import_job_id` char(36)      NOT NULL,
    `relative_path`      varchar(1000) NOT NULL,
    `resolved_folder_id` bigint                 DEFAULT NULL,
    `file_size`          bigint        NOT NULL,
    `file_last_modified` bigint                 DEFAULT NULL,
    `mime_type`          varchar(200)           DEFAULT NULL,
    `status`             enum ('PENDING','UPLOADING','DONE','SKIPPED','FAILED') NOT NULL,
    `skip_reason`        varchar(500)           DEFAULT NULL,
    `error_message`      varchar(500)           DEFAULT NULL,
    `resource_id`        bigint                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_bulk_import_items_job` (`bulk_import_job_id`),
    KEY `idx_bulk_import_items_job_status` (`bulk_import_job_id`, `status`),
    CONSTRAINT `fk_bulk_import_items_job` FOREIGN KEY (`bulk_import_job_id`) REFERENCES `bulk_import_jobs` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bulk_import_items_folder` FOREIGN KEY (`resolved_folder_id`) REFERENCES `folders` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_bulk_import_items_resource` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Hinweis: bulk_import_jobs.target_folder_id nutzt ON DELETE CASCADE (löscht bei
-- Ordner-Löschung auch die Job-Historie mit) statt ON DELETE SET NULL — Aufräum-/
-- Retentions-Verhalten für Bulk-Import-Datensätze ist laut Spec ein offener Punkt;
-- diese Wahl ist eine bewusste Zwischenlösung, kein Versehen.

-- Verknüpfung von upload_jobs zurück zum auslösenden BulkImportItem (s. Task 12/13)
ALTER TABLE `upload_jobs`
    ADD COLUMN `bulk_import_item_id` bigint DEFAULT NULL AFTER `file_last_modified`,
    ADD KEY `idx_upload_jobs_bulk_import_item` (`bulk_import_item_id`),
    ADD CONSTRAINT `fk_upload_jobs_bulk_import_item` FOREIGN KEY (`bulk_import_item_id`) REFERENCES `bulk_import_items` (`id`) ON DELETE SET NULL;
