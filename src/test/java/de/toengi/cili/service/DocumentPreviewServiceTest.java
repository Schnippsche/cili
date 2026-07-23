package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentPreviewServiceTest {

    @TempDir Path tempDir;

    @Mock ResourceRepository resourceRepository;
    @Mock StorageService storageService;
    @Mock CommandRunner commandRunner;

    private FileStorageConfig config;
    private DocumentPreviewService documentPreviewService;

    @BeforeEach
    void setUp() {
        config = new FileStorageConfig();
        config.setBasePath(tempDir.toString());
        config.setLibreOfficePath("/usr/bin/soffice");
        // Real LibreOfficeConversionService wired to the mocked CommandRunner — the mock stays
        // the source of truth for exit codes/output-file simulation used by tests below, while
        // the isolated-profile-dir behavior runs for real (not itself under test here, see
        // LibreOfficeConversionServiceTest).
        LibreOfficeConversionService libreOfficeConversionService =
                new LibreOfficeConversionService(config, commandRunner);
        documentPreviewService = new DocumentPreviewService(resourceRepository, storageService, config, libreOfficeConversionService);
    }

    @Test
    void getPreviewPath_resourceNotFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentPreviewService.getPreviewPath(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPreviewPath_cachedPdfExists_returnsWithoutRunningLibreOffice() throws Exception {
        Resource r = resource(1L, "abc", "document.docx");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(r));

        // Pre-create cached PDF
        Path previewDir = tempDir.resolve("previews");
        Files.createDirectories(previewDir);
        Path cachedPdf = previewDir.resolve("abc.pdf");
        Files.writeString(cachedPdf, "%PDF-fake");

        Path result = documentPreviewService.getPreviewPath(1L, 1L);

        assertThat(result).isEqualTo(cachedPdf);
        verifyNoInteractions(commandRunner);
    }

    @Test
    void getPreviewPath_noCache_runsLibreOfficeAndReturnsPdf() throws Exception {
        Resource r = resource(2L, "def", "document.docx");
        when(resourceRepository.findById(2L)).thenReturn(Optional.of(r));

        // Simulate file exists on disk
        Path inputPath = tempDir.resolve("resources").resolve("de").resolve("def");
        Files.createDirectories(inputPath.getParent());
        Files.writeString(inputPath, "fake docx content");
        when(storageService.resolveLocalPath("def")).thenReturn(Optional.of(inputPath));

        // LibreOffice creates {outdir}/{basename}.pdf — simulate it
        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            java.util.List<String> cmd = inv.getArgument(0);
            int outDirIdx = cmd.indexOf("--outdir");
            String outDir = cmd.get(outDirIdx + 1);
            String inputFile = cmd.getLast();
            String baseName = Path.of(inputFile).getFileName().toString()
                    .replaceAll("\\.[^.]+$", "");
            Path libOut = Path.of(outDir).resolve(baseName + ".pdf");
            Files.createDirectories(libOut.getParent());
            Files.writeString(libOut, "%PDF-fake");
            return 0;
        });

        Path result = documentPreviewService.getPreviewPath(2L, 1L);

        assertThat(result.getFileName().toString()).hasToString("def.pdf");
        assertThat(Files.exists(result)).isTrue();
        verify(commandRunner).run(anyList());
    }

    @Test
    void getPreviewPath_libreOfficeFails_throws() throws Exception {
        Resource r = resource(3L, "ghi", "document.pptx");
        when(resourceRepository.findById(3L)).thenReturn(Optional.of(r));

        Path inputPath = tempDir.resolve("ghi");
        Files.writeString(inputPath, "fake");
        when(storageService.resolveLocalPath("ghi")).thenReturn(Optional.of(inputPath));
        when(commandRunner.run(anyList())).thenReturn(1); // LibreOffice fails

        assertThatThrownBy(() -> documentPreviewService.getPreviewPath(3L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LibreOffice");

        // Verify no temp files remain in the system temp dir
        // (the finally block must have cleaned up the temp file)
        long tempFiles = java.nio.file.Files.list(Path.of(System.getProperty("java.io.tmpdir")))
                .filter(p -> p.getFileName().toString().startsWith("cili-preview-"))
                .count();
        assertThat(tempFiles).isZero();
    }

    @Test
    void getPreviewPath_noLocalPath_throws() throws Exception {
        Resource r = resource(4L, "jkl", "document.docx");
        when(resourceRepository.findById(4L)).thenReturn(Optional.of(r));
        when(storageService.resolveLocalPath("jkl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentPreviewService.getPreviewPath(4L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not stored locally");
    }

    private Resource resource(Long id, String storedName, String originalName) {
        return Resource.builder().id(id).folderId(10L).originalName(originalName)
                .storedName(storedName).mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .size(100L).uploaderId(1L).storageType(StorageType.LOCAL)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }
}
