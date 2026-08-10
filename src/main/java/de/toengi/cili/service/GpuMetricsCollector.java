package de.toengi.cili.service;

import de.toengi.cili.model.entity.GpuMetricSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class GpuMetricsCollector {

    private static final long WARN_THROTTLE_MS = 60_000;

    private final NvidiaSmiExecutor executor;

    private final AtomicLong lastWarnMillis = new AtomicLong(0);

    public Optional<GpuMetricSample> collect() {
        Optional<String> line = executor.execute();
        if (line.isEmpty()) {
            warnThrottled("nvidia-smi lieferte keine Ausgabe (nicht installiert, kein Treiber, oder Timeout)");
            return Optional.empty();
        }
        try {
            return Optional.of(parse(line.get()));
        } catch (RuntimeException e) {
            warnThrottled("nvidia-smi-Ausgabe konnte nicht geparst werden: " + line.get() + " (" + e.getMessage() + ")");
            return Optional.empty();
        }
    }

    private GpuMetricSample parse(String csvLine) {
        String[] parts = csvLine.split(",");
        if (parts.length != 8) {
            throw new IllegalArgumentException("Erwartet 8 Felder, erhalten: " + parts.length);
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return GpuMetricSample.builder()
            .sampledAt(LocalDateTime.now())
            .temperatureC(Integer.parseInt(parts[0]))
            .powerDrawW(new BigDecimal(parts[1]))
            .memoryUsedMb(Integer.parseInt(parts[2]))
            .memoryTotalMb(Integer.parseInt(parts[3]))
            .utilizationGpuPct(Integer.parseInt(parts[4]))
            .fanSpeedPct(parseOptionalInt(parts[5]))
            .clockSmMhz(Integer.parseInt(parts[6]))
            .clockMemoryMhz(Integer.parseInt(parts[7]))
            .build();
    }

    private Integer parseOptionalInt(String value) {
        if (value.contains("N/A")) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        long last = lastWarnMillis.get();
        if (now - last >= WARN_THROTTLE_MS && lastWarnMillis.compareAndSet(last, now)) {
            log.warn("[gpu-monitoring] {}", message);
        }
    }
}
