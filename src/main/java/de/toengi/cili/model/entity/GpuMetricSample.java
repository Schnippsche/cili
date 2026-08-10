package de.toengi.cili.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gpu_metric_samples")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class GpuMetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sampled_at", nullable = false)
    private LocalDateTime sampledAt;

    @Column(name = "temperature_c", nullable = false)
    private Integer temperatureC;

    @Column(name = "power_draw_w", nullable = false, precision = 6, scale = 2)
    private BigDecimal powerDrawW;

    @Column(name = "memory_used_mb", nullable = false)
    private Integer memoryUsedMb;

    @Column(name = "memory_total_mb", nullable = false)
    private Integer memoryTotalMb;

    @Column(name = "utilization_gpu_pct", nullable = false)
    private Integer utilizationGpuPct;

    @Column(name = "fan_speed_pct")
    private Integer fanSpeedPct;

    @Column(name = "clock_sm_mhz", nullable = false)
    private Integer clockSmMhz;

    @Column(name = "clock_memory_mhz", nullable = false)
    private Integer clockMemoryMhz;
}
