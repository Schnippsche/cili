package de.toengi.cili.config;

import de.toengi.cili.model.entity.GpuMetricSample;
import de.toengi.cili.repository.GpuMetricSampleRepository;
import de.toengi.cili.service.GpuMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GpuMetricsSchedulerTest {

    @Mock GpuMetricsCollector collector;
    @Mock GpuMetricSampleRepository repository;

    GpuMonitoringConfig config;
    GpuMetricsScheduler scheduler;

    @BeforeEach
    void setUp() {
        config = new GpuMonitoringConfig();
        config.setEnabled(true);
        scheduler = new GpuMetricsScheduler(config, collector, repository);
    }

    @Test
    void sample_disabled_skipsCollectionEntirely() {
        config.setEnabled(false);
        scheduler.sample();
        verifyNoInteractions(collector, repository);
    }

    @Test
    void sample_collectorReturnsEmpty_doesNotSave() {
        when(collector.collect()).thenReturn(Optional.empty());
        scheduler.sample();
        verifyNoInteractions(repository);
    }

    @Test
    void sample_collectorReturnsSample_savesIt() {
        GpuMetricSample sample = GpuMetricSample.builder().temperatureC(60).build();
        when(collector.collect()).thenReturn(Optional.of(sample));
        scheduler.sample();
        verify(repository).save(sample);
    }
}
