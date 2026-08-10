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
        List<String> command = buildCommand();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Read stdout on a separate daemon thread so waitFor() timeout is not defeated by blocking read
            StringBuilder output = new StringBuilder();
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null) {
                        output.append(line);
                    }
                } catch (IOException ignored) {
                    // process was likely killed by the timeout branch below
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }

            // Join reader thread to ensure output has been fully written before reading it
            try {
                readerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }

            // If reader thread is still alive after timeout, the pipe is likely stuck; treat as no usable output
            if (readerThread.isAlive()) {
                return Optional.empty();
            }

            if (process.exitValue() != 0 || output.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(output.toString().trim());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /**
     * Builds the nvidia-smi command list. Protected to allow testing with different commands.
     */
    protected List<String> buildCommand() {
        return List.of(
            config.getNvidiaSmiPath(),
            "--query-gpu=" + String.join(",", QUERY_FIELDS),
            "--format=csv,noheader,nounits");
    }
}
