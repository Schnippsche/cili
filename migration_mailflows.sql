-- migration_mailflows.sql
-- Produktiv-Migration für Mailflow-Automation (Feature: mailflow-automation)
-- Neue Tabellen: mailflow_instances, mailflow_step_status
--
-- Voraussetzung: `customers`- und `users`-Tabellen existieren bereits.
-- Vor dem Ausführen: Backup der Produktiv-DB nicht vergessen.

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
