-- migration_customers.sql
-- Neue Tabelle: Kunden/Interessenten, die ein Sponsor (User) eintraegt,
-- inkl. Single-Opt-in-Consent, permanentem Abmelde-Token und optionalen Profilfeldern
-- (fuer Anrede/spaetere Erinnerungsmails, alle nullable, da diese Runde keinen Update-Endpoint hat).
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
