package de.toengi.cili.config;

import de.toengi.cili.repository.GpuMetricSampleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class GpuMetricsCleanupScheduler {

    private final GpuMonitoringConfig config;
    private final GpuMetricSampleRepository repository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanup() {
        if (!config.isEnabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(config.getRetentionDays());
        long deleted = repository.deleteBySampledAtBefore(cutoff);
        if (deleted > 0) {
            log.info("[gpu-monitoring] {} alte GPU-Samples gelöscht (älter als {} Tage)", deleted, config.getRetentionDays());
        }
    }
}
