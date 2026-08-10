package de.toengi.cili.service;

import de.toengi.cili.model.entity.GpuMetricSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GpuMetricsCollectorTest {

    @Mock NvidiaSmiExecutor executor;

    GpuMetricsCollector collector;

    @Test
    void collect_validLine_returnsParsedSample() {
        collector = new GpuMetricsCollector(executor);
        when(executor.execute()).thenReturn(Optional.of("62, 145.30, 8123, 24576, 34, 45, 1830, 9501"));

        Optional<GpuMetricSample> result = collector.collect();

        assertThat(result).isPresent();
        GpuMetricSample sample = result.get();
        assertThat(sample.getTemperatureC()).isEqualTo(62);
        assertThat(sample.getPowerDrawW()).isEqualByComparingTo(new BigDecimal("145.30"));
        assertThat(sample.getMemoryUsedMb()).isEqualTo(8123);
        assertThat(sample.getMemoryTotalMb()).isEqualTo(24576);
        assertThat(sample.getUtilizationGpuPct()).isEqualTo(34);
        assertThat(sample.getFanSpeedPct()).isEqualTo(45);
        assertThat(sample.getClockSmMhz()).isEqualTo(1830);
        assertThat(sample.getClockMemoryMhz()).isEqualTo(9501);
        assertThat(sample.getSampledAt()).isNotNull();
    }

    @Test
    void collect_fanSpeedNotAvailable_storesNullFanSpeed() {
        collector = new GpuMetricsCollector(executor);
        when(executor.execute()).thenReturn(Optional.of("62, 145.30, 8123, 24576, 34, [N/A], 1830, 9501"));

        Optional<GpuMetricSample> result = collector.collect();

        assertThat(result).isPresent();
        assertThat(result.get().getFanSpeedPct()).isNull();
    }

    @Test
    void collect_executorReturnsEmpty_returnsEmpty() {
        collector = new GpuMetricsCollector(executor);
        when(executor.execute()).thenReturn(Optional.empty());

        assertThat(collector.collect()).isEmpty();
    }

    @Test
    void collect_malformedLine_returnsEmpty() {
        collector = new GpuMetricsCollector(executor);
        when(executor.execute()).thenReturn(Optional.of("not,enough,fields"));

        assertThat(collector.collect()).isEmpty();
    }

    @Test
    void collect_nonNumericField_returnsEmpty() {
        collector = new GpuMetricsCollector(executor);
        when(executor.execute()).thenReturn(Optional.of("abc, 145.30, 8123, 24576, 34, 45, 1830, 9501"));

        assertThat(collector.collect()).isEmpty();
    }
}
