package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentPreviewService {

    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final FileStorageConfig config;
    private final LibreOfficeConversionService libreOfficeConversionService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public Path getPreviewPath(Long resourceId, Long userId) throws IOException {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));

        Path previewDir = Paths.get(config.getBasePath(), "previews");
        Path previewPath = previewDir.resolve(resource.getStoredName() + ".pdf");

        // Two concurrent requests may both pass this check and both run LibreOffice.
        // Files.move with REPLACE_EXISTING is atomic on Linux, so the final file is always valid.
        if (Files.exists(previewPath)) return previewPath;

        Path inputPath = storageService.resolveLocalPath(resource.getStoredName())
                .orElseThrow(() -> new IllegalStateException("Resource not stored locally: " + resource.getStoredName()));

        // Native PDFs are already in the right format — skip LibreOffice to avoid re-render artifacts.
        if ("application/pdf".equals(resource.getMimeType())) {
            Files.createDirectories(previewDir);
            Files.copy(inputPath, previewPath, StandardCopyOption.REPLACE_EXISTING);
            return previewPath;
        }

        // LibreOffice needs correct extension to detect file type
        String ext = extension(resource.getOriginalName());
        Path tempInput = Files.createTempFile("cili-preview-", "." + ext);
        try {
            Files.copy(inputPath, tempInput, StandardCopyOption.REPLACE_EXISTING);
            Files.createDirectories(previewDir);

            int exitCode;
            try {
                exitCode = libreOfficeConversionService.convert("pdf", previewDir, tempInput);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("LibreOffice conversion interrupted", e);
            }

            // LibreOffice writes {basename}.pdf into outdir
            String baseName = tempInput.getFileName().toString().replaceAll("\\.[^.]+$", "");
            Path libOut = previewDir.resolve(baseName + ".pdf");

            if (exitCode != 0 || !Files.exists(libOut)) {
                throw new IllegalStateException("LibreOffice conversion failed (exit code " + exitCode + ")");
            }
            Files.move(libOut, previewPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempInput);
        }

        return previewPath;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "bin" : filename.substring(dot + 1).toLowerCase();
    }
}
