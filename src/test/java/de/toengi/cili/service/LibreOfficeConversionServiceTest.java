package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibreOfficeConversionServiceTest {

    @TempDir Path tempDir;

    @Mock CommandRunner commandRunner;

    private FileStorageConfig config;
    private LibreOfficeConversionService service;

    @BeforeEach
    void setUp() {
        config = new FileStorageConfig();
        config.setLibreOfficePath("/usr/bin/soffice");
        service = new LibreOfficeConversionService(config, commandRunner);
    }

    @Test
    void convert_buildsCommandWithIsolatedExistingUserProfileAndCorrectArgs() throws Exception {
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);
        Path input = tempDir.resolve("input.docx");
        Files.writeString(input, "fake");

        ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        // Verifying the profile directory exists AT CALL TIME (inside the answer) proves
        // real isolation, not just a string that happens to look like a path.
        when(commandRunner.run(cmdCaptor.capture())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String profileArg = cmd.stream()
                    .filter(a -> a.startsWith("-env:UserInstallation="))
                    .findFirst().orElseThrow();
            String profilePath = profileArg.substring("-env:UserInstallation=file://".length());
            assertThat(Files.isDirectory(Path.of(profilePath))).isTrue();
            return 0;
        });

        int rc = service.convert("pdf", outDir, input);

        assertThat(rc).isEqualTo(0);
        List<String> cmd = cmdCaptor.getValue();
        assertThat(cmd.get(0)).isEqualTo("/usr/bin/soffice");
        assertThat(cmd).anyMatch(a -> a.startsWith("-env:UserInstallation=file://"));
        assertThat(cmd).contains("--headless", "--convert-to", "pdf", "--outdir", outDir.toString());
        assertThat(cmd.get(cmd.size() - 1)).isEqualTo(input.toString());
    }

    @Test
    void convert_deletesProfileDirectoryAfterSuccessfulRun() throws Exception {
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);
        Path input = tempDir.resolve("input.docx");
        Files.writeString(input, "fake");

        ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        when(commandRunner.run(cmdCaptor.capture())).thenReturn(0);

        service.convert("pdf", outDir, input);

        String profileArg = cmdCaptor.getValue().stream()
                .filter(a -> a.startsWith("-env:UserInstallation="))
                .findFirst().orElseThrow();
        String profilePath = profileArg.substring("-env:UserInstallation=file://".length());
        assertThat(Files.exists(Path.of(profilePath))).isFalse();
    }

    @Test
    void convert_deletesProfileDirectoryEvenWhenCommandRunnerThrows() throws Exception {
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);
        Path input = tempDir.resolve("input.docx");
        Files.writeString(input, "fake");

        ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        when(commandRunner.run(cmdCaptor.capture())).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.convert("pdf", outDir, input))
                .isInstanceOf(IOException.class)
                .hasMessage("boom");

        String profileArg = cmdCaptor.getValue().stream()
                .filter(a -> a.startsWith("-env:UserInstallation="))
                .findFirst().orElseThrow();
        String profilePath = profileArg.substring("-env:UserInstallation=file://".length());
        assertThat(Files.exists(Path.of(profilePath))).isFalse();
    }

    @Test
    void convert_returnsExitCodeFromCommandRunner() throws Exception {
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);
        Path input = tempDir.resolve("input.docx");
        Files.writeString(input, "fake");
        when(commandRunner.run(anyList())).thenReturn(1);

        int rc = service.convert("pdf", outDir, input);

        assertThat(rc).isEqualTo(1);
        verify(commandRunner).run(anyList());
    }

    @Test
    void convert_serializesConcurrentCalls_neverRunsMoreThanOneSofficeAtATime() throws Exception {
        // LibreOffice headless bleibt auch mit isoliertem Profil nicht garantiert
        // nebenläufigkeitssicher (Ursache eines Produktionsvorfalls: CPU-Konkurrenz
        // zwischen gleichzeitigen soffice-Instanzen führte zu Endlosschleifen). Dieser
        // Test beweist, dass niemals zwei convert()-Aufrufe gleichzeitig im
        // CommandRunner ankommen, egal von wie vielen Threads gleichzeitig aufgerufen wird.
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);

        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        CommandRunner slowRunner = cmd -> {
            int current = concurrent.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            try {
                Thread.sleep(150);
            } finally {
                concurrent.decrementAndGet();
            }
            return 0;
        };
        LibreOfficeConversionService serializingService = new LibreOfficeConversionService(config, slowRunner);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = IntStream.range(0, 4)
                    .<Future<?>>mapToObj(i -> pool.submit((Runnable) () -> {
                        try {
                            Path input = tempDir.resolve("input-" + i + ".docx");
                            Files.writeString(input, "fake");
                            serializingService.convert("pdf", outDir, input);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }))
                    .toList();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(maxConcurrent.get()).isEqualTo(1);
    }
}
