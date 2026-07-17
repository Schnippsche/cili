package de.toengi.cili.config;

import de.toengi.cili.util.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ToolsConfig {

    private final FfmpegTranscodeConfig ffmpegConfig;

    private static boolean isFfmpegProgress(String line) {
        // FFmpeg progress lines: "frame= 123 fps=... size=...KiB time=... bitrate=... speed=..."
        return line.contains("bitrate=") && line.contains("speed=");
    }

    @Bean
    public CommandRunner commandRunner() {
        int timeoutMinutes = ffmpegConfig.getTimeoutMinutes();
        return command -> {
            String tool = command.getFirst();
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    r.lines().forEach(line -> {
                        if (isFfmpegProgress(line)) {
                            log.debug("[{}] {}", tool, line);
                        } else if (line.toLowerCase().contains("error")) {
                            log.error("[{}] {}", tool, line);
                        } else if (line.toLowerCase().contains("warning")) {
                            log.warn("[{}] {}", tool, line);
                        } else {
                            log.debug("[{}] {}", tool, line);
                        }
                    });
                } catch (IOException ignored) {}
            });
            stderrThread.setDaemon(true);
            stderrThread.start();

            try {
                if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    throw new IOException("Command timed out after " + timeoutMinutes
                        + " minutes: " + tool);
                }
                return process.exitValue();
            } finally {
                process.destroy();
            }
        };
    }
}
