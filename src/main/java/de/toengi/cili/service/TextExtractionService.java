package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ResourceMetadata;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TextExtractionService {

    private static final Set<String> PDF_TYPES = Set.of("application/pdf");

    // Office-Formate werden via headless LibreOffice (--convert-to txt) extrahiert.
    // Das nutzt die für die Dokumentvorschau ohnehin benötigte LibreOffice-Laufzeit,
    // sodass keine Office-Parsing-Bibliothek (z. B. Apache POI) im WAR liegen muss.
    private static final Set<String> OFFICE_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/msword",                                                      // .doc
            "application/vnd.oasis.opendocument.text",                                 // .odt
            "application/rtf",                                                         // .rtf
            "text/rtf"                                                                 // .rtf (alt. MIME)
    );

    private static final int MAX_CHARS = 500_000;

    private final ResourceRepository resourceRepository;
    private final ResourceMetadataRepository metadataRepository;
    private final StorageService storageService;
    private final FileStorageConfig config;
    private final CommandRunner commandRunner;

    public boolean supports(String mimeType) {
        return PDF_TYPES.contains(mimeType) || OFFICE_TYPES.contains(mimeType);
    }

    @Transactional
    public void extractAndIndex(Long resourceId) {
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) return;

        String text;
        try {
            text = extractText(resource);
        } catch (Exception e) {
            log.warn("Text extraction failed for resource {} ({}): {}", resourceId, resource.getMimeType(), e.getMessage());
            return;
        }
        if (text == null || text.isBlank()) return;

        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
        }

        ResourceMetadata meta = metadataRepository.findByResourceId(resourceId)
                .orElseGet(() -> ResourceMetadata.builder().resourceId(resourceId).build());
        meta.setTextContent(text);
        metadataRepository.save(meta);
        log.info("Indexed {} chars of text for resource {}", text.length(), resourceId);
    }

    private String extractText(Resource resource) throws Exception {
        String mime = resource.getMimeType();
        if (PDF_TYPES.contains(mime)) {
            try (InputStream in = storageService.retrieve(resource.getStoredName())) {
                return extractPdf(in);
            }
        } else if (OFFICE_TYPES.contains(mime)) {
            return extractViaLibreOffice(resource);
        }
        return null;
    }

    private String extractPdf(InputStream in) throws Exception {
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    /**
     * Extrahiert Klartext aus Office-Dokumenten (.doc/.docx/.odt/.rtf), indem sie
     * über headless LibreOffice in eine UTF-8-Textdatei konvertiert werden.
     * LibreOffice erkennt das Format anhand der Dateiendung, daher wird in eine
     * Temp-Datei mit korrekter Endung kopiert.
     */
    private String extractViaLibreOffice(Resource resource) throws IOException, InterruptedException {
        String ext = extension(resource.getOriginalName());
        Path tempInput = Files.createTempFile("cili-extract-", "." + ext);
        Path outDir = Files.createTempDirectory("cili-extract-out-");
        try {
            try (InputStream in = storageService.retrieve(resource.getStoredName())) {
                Files.copy(in, tempInput, StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> cmd = List.of(
                    config.getLibreOfficePath(),
                    "--headless", "--convert-to", "txt:Text (encoded):UTF8",
                    "--outdir", outDir.toString(),
                    tempInput.toString());

            int exitCode = commandRunner.run(cmd);

            // LibreOffice schreibt {basename}.txt in outdir
            String baseName = tempInput.getFileName().toString().replaceAll("\\.[^.]+$", "");
            Path txtOut = outDir.resolve(baseName + ".txt");

            if (exitCode != 0 || !Files.exists(txtOut)) {
                throw new IOException("LibreOffice text conversion failed (exit code " + exitCode + ")");
            }
            return Files.readString(txtOut, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(tempInput);
            deleteRecursively(outDir);
        }
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

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "bin" : filename.substring(dot + 1).toLowerCase();
    }
}
