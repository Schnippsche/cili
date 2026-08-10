-- migration_gpu_metric_samples.sql
-- Speichert periodisch erfasste GPU-Kennwerte (nvidia-smi) fuer das Admin-Monitoring.
CREATE TABLE IF NOT EXISTS `gpu_metric_samples`
(
    `id`                   bigint      NOT NULL AUTO_INCREMENT,
    `sampled_at`           datetime(6) NOT NULL,
    `temperature_c`        int         NOT NULL,
    `power_draw_w`         decimal(6,2) NOT NULL,
    `memory_used_mb`       int         NOT NULL,
    `memory_total_mb`      int         NOT NULL,
    `utilization_gpu_pct`  int         NOT NULL,
    `fan_speed_pct`        int         DEFAULT NULL,
    `clock_sm_mhz`         int         NOT NULL,
    `clock_memory_mhz`     int         NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_sampled_at` (`sampled_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
