package de.toengi.cili.service;

import de.toengi.cili.config.GpuMonitoringConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessNvidiaSmiExecutor implements NvidiaSmiExecutor {

    private static final List<String> QUERY_FIELDS = List.of(
        "temperature.gpu", "power.draw", "memory.used", "memory.total",
        "utilization.gpu", "fan.speed", "clocks.sm", "clocks.mem");

    private final GpuMonitoringConfig config;

    @Override
    public Optional<String> execute() {
        List<String> command = List.of(
            config.getNvidiaSmiPath(),
            "--query-gpu=" + String.join(",", QUERY_FIELDS),
            "--format=csv,noheader,nounits");
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            String firstLine;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                firstLine = reader.readLine();
            }
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0 || firstLine == null || firstLine.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(firstLine.trim());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }
}
