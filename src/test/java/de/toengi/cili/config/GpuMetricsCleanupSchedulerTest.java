package de.toengi.cili.config;

import de.toengi.cili.repository.GpuMetricSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GpuMetricsCleanupSchedulerTest {

    @Mock GpuMetricSampleRepository repository;

    GpuMonitoringConfig config;
    GpuMetricsCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        config = new GpuMonitoringConfig();
        config.setEnabled(true);
        config.setRetentionDays(7);
        scheduler = new GpuMetricsCleanupScheduler(config, repository);
    }

    @Test
    void cleanup_disabled_skipsEntirely() {
        config.setEnabled(false);
        scheduler.cleanup();
        verifyNoInteractions(repository);
    }

    @Test
    void cleanup_enabled_deletesOlderThanRetentionDays() {
        scheduler.cleanup();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteBySampledAtBefore(cutoffCaptor.capture());

        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(7);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff,
            org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
    }
}
