package de.toengi.cili.service;

import de.toengi.cili.config.GpuMonitoringConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessNvidiaSmiExecutorTest {

    @Test
    void execute_validCommandProducesOutput_returnsParsedLine() {
        GpuMonitoringConfig config = mock(GpuMonitoringConfig.class);

        // Test executor that uses cmd.exe to echo a realistic CSV line
        ProcessNvidiaSmiExecutor executor = new ProcessNvidiaSmiExecutor(config) {
            @Override
            protected List<String> buildCommand() {
                return List.of("cmd", "/c", "echo 62, 145.30, 8123, 24576, 34, 45, 1830, 9501");
            }
        };

        Optional<String> result = executor.execute();

        assertThat(result).isPresent();
        assertThat(result.get()).contains("62");
        assertThat(result.get()).contains("145.30");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void execute_commandHangsWithoutOutput_timeoutAndReturnEmpty() {
        GpuMonitoringConfig config = mock(GpuMonitoringConfig.class);

        // Test executor that uses cmd.exe with timeout to simulate a hanging process
        // timeout /t 10 sleeps for 10 seconds without output, exceeding the 5s executor timeout
        ProcessNvidiaSmiExecutor executor = new ProcessNvidiaSmiExecutor(config) {
            @Override
            protected List<String> buildCommand() {
                return List.of("cmd", "/c", "timeout /t 10 /nobreak");
            }
        };

        long startTime = System.currentTimeMillis();
        Optional<String> result = executor.execute();
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertThat(result).isEmpty();
        // Should complete within roughly 5-6 seconds (5s timeout + small overhead for process teardown)
        // Definitely should NOT take 10 seconds (which would mean the timeout didn't fire)
        assertThat(elapsedMs).isLessThan(7000);
    }

    @Test
    void execute_commandExitsWithNonZeroCode_returnsEmpty() {
        GpuMonitoringConfig config = mock(GpuMonitoringConfig.class);

        // Test executor that uses cmd.exe with an exit code command
        ProcessNvidiaSmiExecutor executor = new ProcessNvidiaSmiExecutor(config) {
            @Override
            protected List<String> buildCommand() {
                return List.of("cmd", "/c", "exit 1");
            }
        };

        Optional<String> result = executor.execute();

        assertThat(result).isEmpty();
    }

    @Test
    void execute_commandProducesNoOutput_returnsEmpty() {
        GpuMonitoringConfig config = mock(GpuMonitoringConfig.class);

        // Test executor that uses cmd.exe but produces no output
        ProcessNvidiaSmiExecutor executor = new ProcessNvidiaSmiExecutor(config) {
            @Override
            protected List<String> buildCommand() {
                return List.of("cmd", "/c", "exit 0");
            }
        };

        Optional<String> result = executor.execute();

        assertThat(result).isEmpty();
    }
}
