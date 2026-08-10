package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.gpu-monitoring")
@Getter @Setter
public class GpuMonitoringConfig {

    /** Schaltet Erfassung UND Cleanup komplett ab (z.B. Dev-Rechner ohne NVIDIA-GPU). */
    private boolean enabled = true;

    private String nvidiaSmiPath = "nvidia-smi";

    private long sampleIntervalMs = 30_000;

    private int retentionDays = 7;
}
