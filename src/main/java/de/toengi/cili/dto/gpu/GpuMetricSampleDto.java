package de.toengi.cili.dto.gpu;

import de.toengi.cili.model.entity.GpuMetricSample;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GpuMetricSampleDto(
    Long id,
    LocalDateTime sampledAt,
    Integer temperatureC,
    BigDecimal powerDrawW,
    Integer memoryUsedMb,
    Integer memoryTotalMb,
    Integer utilizationGpuPct,
    Integer fanSpeedPct,
    Integer clockSmMhz,
    Integer clockMemoryMhz
) {
    public static GpuMetricSampleDto from(GpuMetricSample s) {
        return new GpuMetricSampleDto(
            s.getId(), s.getSampledAt(), s.getTemperatureC(), s.getPowerDrawW(),
            s.getMemoryUsedMb(), s.getMemoryTotalMb(), s.getUtilizationGpuPct(),
            s.getFanSpeedPct(), s.getClockSmMhz(), s.getClockMemoryMhz()
        );
    }
}
