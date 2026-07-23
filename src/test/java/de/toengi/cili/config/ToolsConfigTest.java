package de.toengi.cili.config;

import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolsConfigTest {

    private final ToolsConfig toolsConfig = new ToolsConfig(new FfmpegTranscodeConfig(), new LibreOfficeConfig());

    @Test
    void buildCommandRunner_onSuccess_returnsExitCode() throws Exception {
        CommandRunner runner = toolsConfig.buildCommandRunner(Duration.ofSeconds(10));

        int rc = runner.run(exitWithCode(0));

        assertThat(rc).isEqualTo(0);
    }

    @Test
    void buildCommandRunner_onNonZeroExit_returnsExitCode() throws Exception {
        CommandRunner runner = toolsConfig.buildCommandRunner(Duration.ofSeconds(10));

        int rc = runner.run(exitWithCode(3));

        assertThat(rc).isEqualTo(3);
    }

    @Test
    void buildCommandRunner_onTimeout_throwsWithToolNameAndDuration() {
        CommandRunner runner = toolsConfig.buildCommandRunner(Duration.ofMillis(300));

        assertThatThrownBy(() -> runner.run(sleepSeconds(30)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("timed out");
    }

    // Hinweis zur Prozessbaum-Tötung (killTree()): Der Produktionsvorfall zeigte, dass
    // process.destroyForcibly() allein einen abgespaltenen soffice.bin-Kindprozess nicht
    // erfasst — er lief 18+ Minuten weiter. Der Fix nutzt die dokumentierte JDK-API
    // Process.descendants(), die auf Linux (Produktionsplattform) zuverlässig alle
    // Nachfahren über /proc ermittelt. Ein automatisierter Test, der genau dieses
    // Fork/Detach-Szenario nachstellt, ließ sich auf Windows-Entwicklungsrechnern nicht
    // zuverlässig bauen: MSYS2/cmd.exe bilden POSIX-Subshell-Forks nicht so auf
    // Windows-Prozess-IDs ab, dass ProcessHandle.descendants() sie konsistent wiederfindet
    // — anders als auf Linux, wo das exakt der Standardfall ist, für den die API gedacht
    // ist. Bewusst kein irreführender/flakiger Test dafür; stattdessen production-seitig
    // verifiziert (siehe Vorfall-Nachbesprechung).

    /** Plattformneutraler "beende mit Exit-Code X"-Befehl, ohne Abhängigkeit von sh/Git Bash. */
    private static List<String> exitWithCode(int code) {
        return isWindows()
                ? List.of("cmd", "/c", "exit " + code)
                : List.of("sh", "-c", "exit " + code);
    }

    /**
     * Plattformneutraler "warte mindestens N Sekunden"-Befehl. Auf Windows bewusst per
     * ping statt "timeout" umgesetzt, da "timeout" ohne echte Konsole (z.B. unter Maven
     * Surefire) sofort mit "Input redirection is not supported" abbricht statt zu warten.
     */
    private static List<String> sleepSeconds(int seconds) {
        return isWindows()
                ? List.of("cmd", "/c", "ping -n " + (seconds + 1) + " 127.0.0.1")
                : List.of("sh", "-c", "sleep " + seconds);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
