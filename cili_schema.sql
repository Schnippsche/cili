/*!40101 SET @OLD_CHARACTER_SET_CLIENT = @@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE = @@TIME_ZONE */;
/*!40103 SET TIME_ZONE = '+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS = 0 */;
/*!40101 SET @OLD_SQL_MODE = @@SQL_MODE, SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES = @@SQL_NOTES, SQL_NOTES = 0 */;

DROP TABLE IF EXISTS `acl_entries`;
CREATE TABLE IF NOT EXISTS `acl_entries`
(
    `id`             bigint                                                                                                                                                   NOT NULL AUTO_INCREMENT,
    `subject_type`   enum ('USER','GROUP') COLLATE utf8mb4_unicode_ci                                                                                                         NOT NULL,
    `subject_id`     bigint                                                                                                                                                   NOT NULL,
    `resource_type`  enum ('FOLDER','RESOURCE','GLOBAL','TESTIMONIALS','COLLECTIONS') COLLATE utf8mb4_unicode_ci                                                               NOT NULL,
    `resource_id`    bigint                                                                                                                                                            DEFAULT NULL,
    `permission`     enum ('READ','WRITE','DELETE','DOWNLOAD','UPLOAD','SHARE','MANAGE_METADATA','MANAGE_SUBTITLES','TRANSLATE_SUBTITLES','ADMIN','MANAGE_TEMPLATES') COLLATE utf8mb4_unicode_ci NOT NULL,
    `grant_type`     enum ('ALLOW','DENY') COLLATE utf8mb4_unicode_ci                                                                                                         NOT NULL DEFAULT 'ALLOW',
    `is_inheritable` tinyint(1)                                                                                                                                               NOT NULL DEFAULT '1',
    `created_at`     datetime(6)                                                                                                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_acl` (`subject_type`, `subject_id`, `resource_type`, `resource_id`,
                         `permission`),
    KEY `idx_acl_resource` (`resource_type`, `resource_id`),
    KEY `idx_acl_subject` (`subject_type`, `subject_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 60
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `ai_summaries`;
CREATE TABLE IF NOT EXISTS `ai_summaries`
(
    `id`            bigint                                 NOT NULL AUTO_INCREMENT,
    `resource_id`   bigint                                 NOT NULL,
    `language_code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
    `summary`       mediumtext COLLATE utf8mb4_unicode_ci,
    `created_at`    datetime(6)                            NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_ai_summaries_resource_lang` (`resource_id`, `language_code`),
    CONSTRAINT `ai_summaries_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 7
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `bulk_import_items`;
CREATE TABLE IF NOT EXISTS `bulk_import_items`
(
    `id`                 bigint                                                                  NOT NULL AUTO_INCREMENT,
    `bulk_import_job_id` char(36) COLLATE utf8mb4_unicode_ci                                      NOT NULL,
    `relative_path`      varchar(1000) COLLATE utf8mb4_unicode_ci                                 NOT NULL,
    `resolved_folder_id` bigint                                                                            DEFAULT NULL,
    `file_size`          bigint                                                                   NOT NULL,
    `file_last_modified` bigint                                                                            DEFAULT NULL,
    `mime_type`          varchar(200) COLLATE utf8mb4_unicode_ci                                           DEFAULT NULL,
    `status`             enum ('PENDING','UPLOADING','DONE','SKIPPED','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL,
    `skip_reason`        varchar(500) COLLATE utf8mb4_unicode_ci                                           DEFAULT NULL,
    `error_message`      varchar(500) COLLATE utf8mb4_unicode_ci                                           DEFAULT NULL,
    `resource_id`        bigint                                                                            DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_bulk_import_items_job` (`bulk_import_job_id`),
    KEY `idx_bulk_import_items_job_status` (`bulk_import_job_id`, `status`),
    CONSTRAINT `fk_bulk_import_items_job` FOREIGN KEY (`bulk_import_job_id`) REFERENCES `bulk_import_jobs` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bulk_import_items_folder` FOREIGN KEY (`resolved_folder_id`) REFERENCES `folders` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_bulk_import_items_resource` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `bulk_import_jobs`;
CREATE TABLE IF NOT EXISTS `bulk_import_jobs`
(
    `id`               char(36) COLLATE utf8mb4_unicode_ci    NOT NULL,
    `admin_user_id`    bigint                                  NOT NULL,
    `target_folder_id` bigint                                  NOT NULL,
    `root_name`        varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `files_total`      int                                     NOT NULL DEFAULT '0',
    `files_done`       int                                     NOT NULL DEFAULT '0',
    `files_skipped`    int                                     NOT NULL DEFAULT '0',
    `files_failed`     int                                     NOT NULL DEFAULT '0',
    `created_at`       datetime(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_bulk_import_jobs_admin` (`admin_user_id`),
    CONSTRAINT `fk_bulk_import_jobs_admin` FOREIGN KEY (`admin_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bulk_import_jobs_folder` FOREIGN KEY (`target_folder_id`) REFERENCES `folders` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `collection`;
CREATE TABLE IF NOT EXISTS `collection`
(
    `id`          bigint                                  NOT NULL AUTO_INCREMENT,
    `user_id`     bigint                                  NOT NULL,
    `name`        varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `created_at`  datetime(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `is_template` tinyint(1)                              NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    KEY `idx_collection_user` (`user_id`),
    CONSTRAINT `fk_collection_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 5
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `collection_item`;
CREATE TABLE IF NOT EXISTS `collection_item`
(
    `id`             bigint      NOT NULL AUTO_INCREMENT,
    `collection_id`  bigint      NOT NULL,
    `resource_id`    bigint               DEFAULT NULL,
    `testimonial_id` bigint               DEFAULT NULL,
    `added_at`       datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_collection_resource` (`collection_id`, `resource_id`),
    UNIQUE KEY `uq_collection_testimonial` (`collection_id`, `testimonial_id`),
    KEY `fk_ci_resource` (`resource_id`),
    KEY `fk_ci_testimonial` (`testimonial_id`),
    CONSTRAINT `fk_ci_collection` FOREIGN KEY (`collection_id`) REFERENCES `collection` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ci_resource` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ci_testimonial` FOREIGN KEY (`testimonial_id`) REFERENCES `testimonials` (`id`) ON DELETE CASCADE,
    CONSTRAINT `chk_ci_item_type` CHECK ((
        ((`resource_id` is not null) and (`testimonial_id` is null)) or
        ((`resource_id` is null) and (`testimonial_id` is not null))))
) ENGINE = InnoDB
  AUTO_INCREMENT = 7
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `collection_share_tokens`;
CREATE TABLE IF NOT EXISTS `collection_share_tokens`
(
    `id`            bigint                                 NOT NULL AUTO_INCREMENT,
    `token`         varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
    `collection_id` bigint                                 NOT NULL,
    `created_by`    bigint                                          DEFAULT NULL,
    `created_at`    datetime(6)                            NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `expires_at`    datetime(6)                            NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_cst_token` (`token`),
    UNIQUE KEY `uq_cst_collection` (`collection_id`),
    KEY `fk_cst_user` (`created_by`),
    CONSTRAINT `fk_cst_collection` FOREIGN KEY (`collection_id`) REFERENCES `collection` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_cst_user` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  AUTO_INCREMENT = 10
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `customers`;
CREATE TABLE IF NOT EXISTS `customers`
(
    `id`                  bigint                                  NOT NULL AUTO_INCREMENT,
    `name`                varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `first_name`          varchar(100) COLLATE utf8mb4_unicode_ci          DEFAULT NULL,
    `email`               varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `mobile_phone`        varchar(50) COLLATE utf8mb4_unicode_ci           DEFAULT NULL,
    `birth_date`          date                                             DEFAULT NULL,
    `member_id`           int                                              DEFAULT NULL,
    `gender`              enum ('MAENNLICH','WEIBLICH') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `informal_address`    tinyint(1)                                       DEFAULT NULL,
    `sponsor_user_id`     bigint                                  NOT NULL,
    `consent_granted`     tinyint(1)                              NOT NULL DEFAULT 1,
    `consent_granted_at`  datetime(6)                             NOT NULL,
    `consent_revoked_at`  datetime(6)                                      DEFAULT NULL,
    `unsubscribe_token`   varchar(36) COLLATE utf8mb4_unicode_ci  NOT NULL,
    `created_at`          datetime(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`          datetime(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_customers_sponsor_email` (`sponsor_user_id`, `email`),
    UNIQUE KEY `uq_customers_unsubscribe_token` (`unsubscribe_token`),
    CONSTRAINT `fk_customers_sponsor` FOREIGN KEY (`sponsor_user_id`) REFERENCES `users` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `folder_favorites`;
CREATE TABLE IF NOT EXISTS `folder_favorites`
(
    `user_id`   bigint      NOT NULL,
    `folder_id` bigint      NOT NULL,
    `added_at`  datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`user_id`, `folder_id`),
    KEY `folder_id` (`folder_id`),
    CONSTRAINT `folder_favorites_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `folder_favorites_ibfk_2` FOREIGN KEY (`folder_id`) REFERENCES `folders` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `folders`;
CREATE TABLE IF NOT EXISTS `folders`
(
    `id`          bigint                                   NOT NULL AUTO_INCREMENT,
    `name`        varchar(255) COLLATE utf8mb4_unicode_ci  NOT NULL,
    `parent_id`   bigint                                            DEFAULT NULL,
    `path`        varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '/',
    `description` text COLLATE utf8mb4_unicode_ci,
    `is_trashed`  tinyint(1)                               NOT NULL DEFAULT '0',
    `trashed_at`  datetime(6)                                       DEFAULT NULL,
    `created_by`  bigint                                   NOT NULL,
    `created_at`  datetime(6)                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`  datetime(6)                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_folders_parent` (`parent_id`),
    KEY `idx_folders_path` (`path`(255)),
    KEY `idx_folders_created_by` (`created_by`),
    KEY `idx_folders_trashed` (`is_trashed`),
    FULLTEXT KEY `ft_folders_name` (`name`),
    CONSTRAINT `folders_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `folders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `folders_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 39
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `language_options`;
CREATE TABLE IF NOT EXISTS `language_options`
(
    `code`                  varchar(10) COLLATE utf8mb4_unicode_ci  NOT NULL,
    `label`                 varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    `translation_supported` tinyint(1)                              NOT NULL DEFAULT '0',
    `sort_order`            int                                     NOT NULL DEFAULT '0',
    `enabled`               tinyint(1)                              NOT NULL DEFAULT '1',
    PRIMARY KEY (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `mailflow_instances`;
CREATE TABLE IF NOT EXISTS `mailflow_instances`
(
    `id`                 bigint                                  NOT NULL AUTO_INCREMENT,
    `customer_id`        bigint                                  NOT NULL,
    `flow_name`          varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    `started_at`         datetime(6)                             NOT NULL,
    `created_by_user_id` bigint                                  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_mailflow_instances_customer_flow` (`customer_id`, `flow_name`),
    CONSTRAINT `fk_mailflow_instances_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
    CONSTRAINT `fk_mailflow_instances_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `mailflow_step_status`;
CREATE TABLE IF NOT EXISTS `mailflow_step_status`
(
    `id`            bigint                                  NOT NULL AUTO_INCREMENT,
    `instance_id`   bigint                                  NOT NULL,
    `step_id`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    `scheduled_for` date                                    NOT NULL,
    `sent_at`       datetime(6)                                      DEFAULT NULL,
    `status`        varchar(20) COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'PENDING',
    `attempt_count` int                                     NOT NULL DEFAULT 0,
    `last_error`    varchar(1000) COLLATE utf8mb4_unicode_ci         DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mailflow_step_status_instance_step` (`instance_id`, `step_id`),
    KEY `idx_mailflow_step_status_due` (`status`, `scheduled_for`),
    CONSTRAINT `fk_mailflow_step_status_instance` FOREIGN KEY (`instance_id`)
        REFERENCES `mailflow_instances` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `processing_jobs`;
CREATE TABLE IF NOT EXISTS `processing_jobs`
(
    `id`            bigint                                                                                                                                                                                                                                                             NOT NULL AUTO_INCREMENT,
    `resource_id`   bigint                                                                                                                                                                                                                                                                      DEFAULT NULL,
    `type`          enum ('THUMBNAIL','OCR','PREVIEW','TRANSCODE','VIDEO_ANALYSIS','VIDEO_TRANSCODE','WAV_EXTRACT','WHISPER_TRANSCRIBE','AUDIO_NORMALIZE','SUBTITLE_TRANSLATE','DOCUMENT_TRANSLATE','OLLAMA_ANALYSIS','TELEGRAM_IMPORT','VIDEO_URL_IMPORT','VIDEO_CLIP','TESTIMONIAL_SUMMARY') COLLATE utf8mb4_unicode_ci NOT NULL,
    `source`        varchar(100) COLLATE utf8mb4_unicode_ci                                                                                                                                                                                                                                     DEFAULT NULL,
    `status`        enum ('PENDING','RUNNING','DONE','FAILED','CANCELLED') COLLATE utf8mb4_unicode_ci                                                                                                                                                                                  NOT NULL DEFAULT 'PENDING',
    `attempts`      int                                                                                                                                                                                                                                                                NOT NULL DEFAULT '0',
    `max_attempts`  int                                                                                                                                                                                                                                                                NOT NULL DEFAULT '3',
    `error_message` text COLLATE utf8mb4_unicode_ci,
    `result`        longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
    `worker_lock`   varchar(255) COLLATE utf8mb4_unicode_ci                                                                                                                                                                                                                                     DEFAULT NULL,
    `started_at`    datetime(6)                                                                                                                                                                                                                                                                 DEFAULT NULL,
    `finished_at`   datetime(6)                                                                                                                                                                                                                                                                 DEFAULT NULL,
    `parent_job_id` bigint                                                                                                                                                                                                                                                                      DEFAULT NULL,
    `created_at`    datetime(6)                                                                                                                                                                                                                                                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`    datetime(6)                                                                                                                                                                                                                                                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_jobs_status` (`status`, `type`),
    KEY `idx_jobs_resource` (`resource_id`),
    CONSTRAINT `processing_jobs_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE,
    CONSTRAINT `processing_jobs_chk_1` CHECK (json_valid(`result`))
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `refresh_tokens`;
CREATE TABLE IF NOT EXISTS `refresh_tokens`
(
    `id`         bigint                                 NOT NULL AUTO_INCREMENT,
    `user_id`    bigint                                 NOT NULL,
    `token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
    `expires_at` datetime(6)                            NOT NULL,
    `created_at` datetime(6)                            NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `revoked_at` datetime(6)                                     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `token_hash` (`token_hash`),
    KEY `idx_refresh_tokens_user` (`user_id`),
    KEY `idx_refresh_tokens_expires` (`expires_at`),
    CONSTRAINT `refresh_tokens_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 385
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `report_jobs`;
CREATE TABLE IF NOT EXISTS `report_jobs`
(
    `id`            bigint                                                                NOT NULL AUTO_INCREMENT,
    `user_id`       bigint                                                                NOT NULL,
    `query`         varchar(500) COLLATE utf8mb4_unicode_ci                                        DEFAULT NULL,
    `status`        enum ('PENDING','RUNNING','DONE','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
    `attempts`      int                                                                   NOT NULL DEFAULT '0',
    `stored_name`   varchar(255) COLLATE utf8mb4_unicode_ci                                        DEFAULT NULL,
    `filename`      varchar(255) COLLATE utf8mb4_unicode_ci                                        DEFAULT NULL,
    `error_message` text COLLATE utf8mb4_unicode_ci,
    `created_at`    datetime(6)                                                           NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`    datetime(6)                                                           NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `finished_at`   datetime(6)                                                                    DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_report_jobs_user` (`user_id`),
    KEY `idx_report_jobs_status` (`status`),
    CONSTRAINT `fk_report_jobs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `resource_favorites`;
CREATE TABLE IF NOT EXISTS `resource_favorites`
(
    `user_id`     bigint      NOT NULL,
    `resource_id` bigint      NOT NULL,
    `added_at`    datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`user_id`, `resource_id`),
    KEY `resource_id` (`resource_id`),
    CONSTRAINT `resource_favorites_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `resource_favorites_ibfk_2` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `resource_metadata`;
CREATE TABLE IF NOT EXISTS `resource_metadata`
(
    `id`            bigint      NOT NULL AUTO_INCREMENT,
    `resource_id`   bigint      NOT NULL,
    `title`         varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `description`   text COLLATE utf8mb4_unicode_ci,
    `tags`          text COLLATE utf8mb4_unicode_ci,
    `categories`    text COLLATE utf8mb4_unicode_ci,
    `language`      varchar(50) COLLATE utf8mb4_unicode_ci  DEFAULT NULL,
    `custom_fields` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
    `updated_at`    datetime(6) NOT NULL                    DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `text_content`  mediumtext COLLATE utf8mb4_unicode_ci,
    PRIMARY KEY (`id`),
    UNIQUE KEY `resource_id` (`resource_id`),
    FULLTEXT KEY `ft_metadata_search` (`title`, `description`, `tags`, `categories`),
    FULLTEXT KEY `ft_metadata_content` (`text_content`),
    CONSTRAINT `resource_metadata_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE,
    CONSTRAINT `resource_metadata_chk_1` CHECK (json_valid(`custom_fields`))
) ENGINE = InnoDB
  AUTO_INCREMENT = 413
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `resources`;
CREATE TABLE IF NOT EXISTS `resources`
(
    `id`             bigint                                            NOT NULL AUTO_INCREMENT,
    `folder_id`      bigint                                                     DEFAULT NULL,
    `testimonial_id` bigint                                                     DEFAULT NULL,
    `original_name`  varchar(500) COLLATE utf8mb4_unicode_ci           NOT NULL,
    `stored_name`    varchar(36) COLLATE utf8mb4_unicode_ci            NOT NULL,
    `mime_type`      varchar(200) COLLATE utf8mb4_unicode_ci           NOT NULL,
    `size`           bigint                                            NOT NULL DEFAULT '0',
    `checksum`       varchar(64) COLLATE utf8mb4_unicode_ci                     DEFAULT NULL,
    `uploader_id`    bigint                                            NOT NULL,
    `storage_type`   enum ('LOCAL','MINIO') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LOCAL',
    `created_at`     datetime(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`     datetime(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `file_date`      datetime                                                   DEFAULT NULL,
    `sort_order`     bigint                                                     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `stored_name` (`stored_name`),
    KEY `idx_resources_folder` (`folder_id`),
    KEY `idx_resources_sort` (`folder_id`, `sort_order`),
    KEY `idx_resources_testimonial` (`testimonial_id`),
    KEY `idx_resources_uploader` (`uploader_id`),
    KEY `idx_resources_mime` (`mime_type`(50)),
    FULLTEXT KEY `ft_resources_name` (`original_name`),
    CONSTRAINT `resources_ibfk_1` FOREIGN KEY (`folder_id`) REFERENCES `folders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `resources_ibfk_2` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`),
    CONSTRAINT `resources_ibfk_3` FOREIGN KEY (`testimonial_id`) REFERENCES `testimonials` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 733
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `rights_groups`;
CREATE TABLE IF NOT EXISTS `rights_groups`
(
    `id`          bigint                                  NOT NULL AUTO_INCREMENT,
    `name`        varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    `description` text COLLATE utf8mb4_unicode_ci,
    `created_at`  datetime(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `is_system`   tinyint(1)                              NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `name` (`name`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 5
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `share_tokens`;
CREATE TABLE IF NOT EXISTS `share_tokens`
(
    `id`          bigint      NOT NULL AUTO_INCREMENT,
    `token`       varchar(36) NOT NULL,
    `resource_id` bigint      NOT NULL,
    `created_by`  bigint               DEFAULT NULL,
    `created_at`  datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `expires_at`  datetime(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_share_token` (`token`),
    UNIQUE KEY `uq_share_resource` (`resource_id`),
    KEY `created_by` (`created_by`),
    CONSTRAINT `share_tokens_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE,
    CONSTRAINT `share_tokens_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  AUTO_INCREMENT = 15
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `subtitle_tracks`;
CREATE TABLE IF NOT EXISTS `subtitle_tracks`
(
    `id`            bigint                                        NOT NULL AUTO_INCREMENT,
    `resource_id`   bigint                                        NOT NULL,
    `language_code` varchar(10) COLLATE utf8mb4_unicode_ci        NOT NULL,
    `label`         varchar(100) COLLATE utf8mb4_unicode_ci                DEFAULT NULL,
    `stored_name`   varchar(36) COLLATE utf8mb4_unicode_ci        NOT NULL,
    `format`        enum ('SRT','VTT') COLLATE utf8mb4_unicode_ci NOT NULL,
    `created_at`    datetime(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `text_content`  mediumtext COLLATE utf8mb4_unicode_ci,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_subtitle_lang` (`resource_id`, `language_code`),
    FULLTEXT KEY `ft_subtitle_content` (`text_content`),
    CONSTRAINT `subtitle_tracks_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 299
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `testimonial_tmp_photos`;
CREATE TABLE IF NOT EXISTS `testimonial_tmp_photos`
(
    `uuid`       varchar(128) DEFAULT NULL,
    `photo_name` varchar(128) DEFAULT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `testimonials`;
CREATE TABLE IF NOT EXISTS `testimonials`
(
    `id`          bigint                                  NOT NULL AUTO_INCREMENT,
    `author_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `text`        text COLLATE utf8mb4_unicode_ci         NOT NULL,
    `is_human`    tinyint(1)                              NOT NULL,
    `is_animal`   tinyint(1)                              NOT NULL,
    `tags`        varchar(500) COLLATE utf8mb4_unicode_ci          DEFAULT NULL,
    `user_id`     bigint                                  NOT NULL,
    `created_at`  datetime(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`  datetime(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_testimonials_user` (`user_id`),
    FULLTEXT KEY `ft_testimonials` (`author_name`, `text`, `tags`),
    CONSTRAINT `fk_testimonials_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `chk_testimonials_category` CHECK ((
        `is_human` = 1 OR `is_animal` = 1))
) ENGINE = InnoDB
  AUTO_INCREMENT = 1006
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `thumbnails`;
CREATE TABLE IF NOT EXISTS `thumbnails`
(
    `id`            bigint                                                                   NOT NULL AUTO_INCREMENT,
    `resource_id`   bigint                                                                   NOT NULL,
    `status`        enum ('PENDING','PROCESSING','DONE','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
    `small_path`    varchar(500) COLLATE utf8mb4_unicode_ci                                           DEFAULT NULL,
    `large_path`    varchar(500) COLLATE utf8mb4_unicode_ci                                           DEFAULT NULL,
    `error_message` text COLLATE utf8mb4_unicode_ci,
    `created_at`    datetime(6)                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`    datetime(6)                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `resource_id` (`resource_id`),
    CONSTRAINT `thumbnails_ibfk_1` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 720
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `upload_jobs`;
CREATE TABLE IF NOT EXISTS `upload_jobs`
(
    `id`                 char(36) COLLATE utf8mb4_unicode_ci                                                          NOT NULL,
    `user_id`            bigint                                                                                       NOT NULL,
    `folder_id`          bigint                                                                                                DEFAULT NULL,
    `testimonial_id`     bigint                                                                                                DEFAULT NULL,
    `file_name`          varchar(500) COLLATE utf8mb4_unicode_ci                                                      NOT NULL,
    `mime_type`          varchar(200) COLLATE utf8mb4_unicode_ci                                                      NOT NULL,
    `total_size`         bigint                                                                                       NOT NULL,
    `chunk_size`         int                                                                                          NOT NULL,
    `chunks_total`       int                                                                                          NOT NULL,
    `chunks_received`    int                                                                                          NOT NULL DEFAULT '0',
    `status`             enum ('INITIATED','IN_PROGRESS','COMPLETE','PROCESSING','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INITIATED',
    `stored_name`        varchar(36) COLLATE utf8mb4_unicode_ci                                                                DEFAULT NULL,
    `created_at`         datetime(6)                                                                                  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `expires_at`         datetime(6)                                                                                  NOT NULL,
    `file_last_modified` bigint                                                                                                DEFAULT NULL,
    `bulk_import_item_id` bigint                                                                                               DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `folder_id` (`folder_id`),
    KEY `idx_upload_jobs_testimonial` (`testimonial_id`),
    KEY `idx_upload_jobs_user` (`user_id`),
    KEY `idx_upload_jobs_status` (`status`),
    KEY `idx_upload_jobs_expires` (`expires_at`),
    KEY `idx_upload_jobs_bulk_import_item` (`bulk_import_item_id`),
    CONSTRAINT `upload_jobs_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `upload_jobs_ibfk_2` FOREIGN KEY (`folder_id`) REFERENCES `folders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_upload_jobs_testimonial` FOREIGN KEY (`testimonial_id`) REFERENCES `testimonials` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_upload_jobs_bulk_import_item` FOREIGN KEY (`bulk_import_item_id`) REFERENCES `bulk_import_items` (`id`) ON DELETE SET NULL,
    CONSTRAINT `chk_upload_jobs_destination` CHECK ((
        (`folder_id` IS NOT NULL AND `testimonial_id` IS NULL) OR
        (`folder_id` IS NULL AND `testimonial_id` IS NOT NULL)))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_group_memberships`;
CREATE TABLE IF NOT EXISTS `user_group_memberships`
(
    `user_id`   bigint      NOT NULL,
    `group_id`  bigint      NOT NULL,
    `joined_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`user_id`, `group_id`),
    KEY `group_id` (`group_id`),
    CONSTRAINT `user_group_memberships_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `user_group_memberships_ibfk_2` FOREIGN KEY (`group_id`) REFERENCES `rights_groups` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `users`;
CREATE TABLE IF NOT EXISTS `users`
(
    `id`            bigint                                           NOT NULL AUTO_INCREMENT,
    `username`      varchar(50) COLLATE utf8mb4_unicode_ci           NOT NULL,
    `email`         varchar(255) COLLATE utf8mb4_unicode_ci          NOT NULL,
    `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci          NOT NULL,
    `display_name`  varchar(100) COLLATE utf8mb4_unicode_ci                   DEFAULT NULL,
    `member_id`     int                                                       DEFAULT NULL,
    `url`           varchar(255) COLLATE utf8mb4_unicode_ci                   DEFAULT NULL,
    `phone`         varchar(50) COLLATE utf8mb4_unicode_ci                    DEFAULT NULL,
    `is_active`     tinyint(1)                                       NOT NULL DEFAULT '1',
    `role`          enum ('ADMIN','USER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER',
    `created_at`    datetime(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`    datetime(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `username` (`username`),
    UNIQUE KEY `email` (`email`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 37
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `video_resumes`;
CREATE TABLE IF NOT EXISTS `video_resumes`
(
    `user_id`          bigint      NOT NULL,
    `resource_id`      bigint      NOT NULL,
    `position_seconds` int         NOT NULL DEFAULT '0',
    `updated_at`       datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`user_id`, `resource_id`),
    KEY `resource_id` (`resource_id`),
    CONSTRAINT `video_resumes_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `video_resumes_ibfk_2` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

/*!40103 SET TIME_ZONE = IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE = IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS = IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT = @OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES = IFNULL(@OLD_SQL_NOTES, 1) */;
