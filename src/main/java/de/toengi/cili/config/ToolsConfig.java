package de.toengi.cili.config;

import de.toengi.cili.util.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ToolsConfig {

    private final FfmpegTranscodeConfig ffmpegConfig;
    private final LibreOfficeConfig libreOfficeConfig;

    private static boolean isFfmpegProgress(String line) {
        // FFmpeg progress lines: "frame= 123 fps=... size=...KiB time=... bitrate=... speed=..."
        return line.contains("bitrate=") && line.contains("speed=");
    }

    /** Standard-Timeout für FFmpeg-lastige Kommandos (Video-Transcode etc.). */
    @Bean
    @Primary
    public CommandRunner commandRunner() {
        return buildCommandRunner(Duration.ofMinutes(ffmpegConfig.getTimeoutMinutes()));
    }

    /**
     * Deutlich kürzerer Timeout speziell für LibreOffice-Kommandos: Ein hängender
     * soffice-Prozess (z.B. durch Profil-Lock-Kontention bei parallelen Konvertierungen)
     * soll schnell beendet werden, nicht erst nach dem für Video-Transcodes bemessenen
     * FFmpeg-Timeout von 60 Minuten — genau das führte zu einem Produktionsvorfall
     * (Swap-Erschöpfung durch mehrere hängende soffice-Prozesse).
     */
    @Bean(name = "libreOfficeCommandRunner")
    public CommandRunner libreOfficeCommandRunner() {
        return buildCommandRunner(Duration.ofMinutes(libreOfficeConfig.getTimeoutMinutes()));
    }

    CommandRunner buildCommandRunner(Duration timeout) {
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
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException("Command timed out after " + timeout
                        + ": " + tool);
                }
                return process.exitValue();
            } finally {
                killTree(process, tool);
            }
        };
    }

    /**
     * Beendet den Prozess UND alle Nachfahren. process.destroyForcibly() erfasst nur den
     * von ProcessBuilder direkt getrackten Prozess — falls dieser (z.B. das Tool selbst,
     * oder ein Wrapper-Skript) weitere Kindprozesse abspaltet, laufen diese sonst als Waisen
     * weiter. Genau das führte zu einem Produktionsvorfall: ein soffice-Prozess überlebte
     * den Timeout-Kill 18+ Minuten lang bei 99% CPU und hielt dabei bereits gelöschte
     * Temp-Dateien offen, bis /tmp trotz fast leerem Verzeichnisinhalt vollständig belegt war.
     */
    private void killTree(Process process, String tool) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                log.error("[{}] Prozess (PID {}) überlebt destroyForcibly() — evtl. manuelles Eingreifen nötig", tool, process.pid());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
