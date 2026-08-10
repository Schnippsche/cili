package de.toengi.cili.config;

import de.toengi.cili.repository.GpuMetricSampleRepository;
import de.toengi.cili.service.GpuMetricsCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GpuMetricsScheduler {

    private final GpuMonitoringConfig config;
    private final GpuMetricsCollector collector;
    private final GpuMetricSampleRepository repository;

    @Scheduled(fixedRateString = "${cili.gpu-monitoring.sample-interval-ms:30000}")
    public void sample() {
        if (!config.isEnabled()) {
            return;
        }
        collector.collect().ifPresent(repository::save);
    }
}
