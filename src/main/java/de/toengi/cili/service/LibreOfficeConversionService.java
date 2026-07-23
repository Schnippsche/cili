package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.util.CommandRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Führt headless-LibreOffice-Konvertierungen aus. Jeder Aufruf bekommt ein eigenes,
 * temporäres User-Profil (-env:UserInstallation), da mehrere gleichzeitige
 * soffice-Prozesse, die sich ein Profilverzeichnis teilen, sich beim Profil-Lock
 * gegenseitig blockieren können — eine wartende Instanz hängt dann, statt sauber
 * zu scheitern (Ursache eines Produktionsvorfalls: parallele Dokument-Uploads
 * führten zu hängenden soffice-Prozessen und Swap-Erschöpfung).
 *
 * Zusätzlich werden alle Aufrufe über ein Semaphore(1) serialisiert: LibreOffice
 * headless ist auch mit isoliertem Profil nicht garantiert nebenläufigkeitssicher —
 * beim selben Vorfall führte CPU-Konkurrenz zwischen gleichzeitigen soffice-Instanzen
 * zu Endlosschleifen statt sauberer Abarbeitung. Serialisierung gilt für alle drei
 * Aufrufer (ThumbnailService, DocumentPreviewService, TextExtractionService)
 * gleichermaßen, unabhängig davon, ob sie über einen Async-Executor oder synchron
 * vom HTTP-Request-Thread aufgerufen werden.
 */
@Service
@Slf4j
public class LibreOfficeConversionService {

    private final FileStorageConfig config;
    private final CommandRunner commandRunner;
    private final Semaphore serializeConversions = new Semaphore(1, true);

    public LibreOfficeConversionService(
            FileStorageConfig config,
            @Qualifier("libreOfficeCommandRunner") CommandRunner commandRunner) {
        this.config = config;
        this.commandRunner = commandRunner;
    }

    /**
     * @param outputFormat LibreOffice --convert-to Filter, z.B. "pdf" oder "txt:Text (encoded):UTF8"
     * @param outDir Zielverzeichnis für die konvertierte Datei
     * @param input Quelldatei
     * @return Exit-Code von soffice
     */
    public int convert(String outputFormat, Path outDir, Path input) throws IOException, InterruptedException {
        serializeConversions.acquire();
        try {
            Path profileDir = Files.createTempDirectory("cili-lo-profile-");
            try {
                List<String> cmd = List.of(
                        config.getLibreOfficePath(),
                        "-env:UserInstallation=file://" + toUnixPath(profileDir),
                        "--headless", "--convert-to", outputFormat,
                        "--outdir", outDir.toString(),
                        input.toString());
                log.debug("[LIBREOFFICE] {}", cmd);
                return commandRunner.run(cmd);
            } finally {
                deleteRecursively(profileDir);
            }
        } finally {
            serializeConversions.release();
        }
    }

    private static String toUnixPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private void deleteRecursively(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
