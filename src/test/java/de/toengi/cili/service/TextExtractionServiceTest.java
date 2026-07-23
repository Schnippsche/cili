package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ResourceMetadata;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TextExtractionServiceTest {

    @TempDir Path tempDir;

    @Mock ResourceRepository resourceRepository;
    @Mock ResourceMetadataRepository metadataRepository;
    @Mock StorageService storageService;
    @Mock CommandRunner commandRunner;

    private TextExtractionService service;

    @BeforeEach
    void setUp() {
        FileStorageConfig config = new FileStorageConfig();
        config.setBasePath(tempDir.toString());
        config.setLibreOfficePath("/usr/bin/soffice");
        // Real LibreOfficeConversionService wired to the mocked CommandRunner — see
        // DocumentPreviewServiceTest for the same pattern and its rationale.
        LibreOfficeConversionService libreOfficeConversionService =
                new LibreOfficeConversionService(config, commandRunner);
        service = new TextExtractionService(
                resourceRepository, metadataRepository, storageService, libreOfficeConversionService);
    }

    @Test
    void supports_acceptsPdfAndOfficeFormats() {
        assertThat(service.supports("application/pdf")).isTrue();
        assertThat(service.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
        assertThat(service.supports("application/msword")).isTrue();
        assertThat(service.supports("application/vnd.oasis.opendocument.text")).isTrue();
        assertThat(service.supports("application/rtf")).isTrue();
        assertThat(service.supports("text/rtf")).isTrue();
        assertThat(service.supports("image/png")).isFalse();
        assertThat(service.supports("video/mp4")).isFalse();
    }

    @Test
    void extractAndIndex_docViaLibreOffice_savesExtractedText() throws Exception {
        Resource r = officeResource(1L, "stored1", "report.doc", "application/msword");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(r));
        when(storageService.retrieve("stored1"))
                .thenReturn(new ByteArrayInputStream("binary doc content".getBytes(StandardCharsets.UTF_8)));
        when(metadataRepository.findByResourceId(1L)).thenReturn(Optional.empty());

        // Simulate LibreOffice writing {basename}.txt with extracted text into outdir.
        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outDir = cmd.get(cmd.indexOf("--outdir") + 1);
            String inputFile = cmd.get(cmd.size() - 1);
            String baseName = Path.of(inputFile).getFileName().toString().replaceAll("\\.[^.]+$", "");
            Path txtOut = Path.of(outDir).resolve(baseName + ".txt");
            Files.writeString(txtOut, "Der extrahierte Text mit Ümlauten.", StandardCharsets.UTF_8);
            return 0;
        });

        service.extractAndIndex(1L);

        ArgumentCaptor<ResourceMetadata> captor = ArgumentCaptor.forClass(ResourceMetadata.class);
        verify(metadataRepository).save(captor.capture());
        assertThat(captor.getValue().getTextContent()).isEqualTo("Der extrahierte Text mit Ümlauten.");

        // Command used the LibreOffice txt filter and the correct extension on the temp input.
        ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(cmdCaptor.capture());
        List<String> cmd = cmdCaptor.getValue();
        assertThat(cmd).contains("--convert-to", "txt:Text (encoded):UTF8");
        assertThat(cmd.get(cmd.size() - 1)).endsWith(".doc");
    }

    @Test
    void extractAndIndex_libreOfficeFails_doesNotSave() throws Exception {
        Resource r = officeResource(2L, "stored2", "notes.odt", "application/vnd.oasis.opendocument.text");
        when(resourceRepository.findById(2L)).thenReturn(Optional.of(r));
        when(storageService.retrieve("stored2"))
                .thenReturn(new ByteArrayInputStream("odt".getBytes(StandardCharsets.UTF_8)));
        when(commandRunner.run(anyList())).thenReturn(1); // conversion fails, no txt written

        service.extractAndIndex(2L);

        verify(metadataRepository, never()).save(any());
    }

    @Test
    void extractAndIndex_resourceMissing_doesNothing() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        service.extractAndIndex(99L);

        verifyNoInteractions(commandRunner);
        verify(metadataRepository, never()).save(any());
    }

    private Resource officeResource(Long id, String storedName, String originalName, String mime) {
        return Resource.builder().id(id).folderId(10L).originalName(originalName)
                .storedName(storedName).mimeType(mime)
                .size(100L).uploaderId(1L).storageType(StorageType.LOCAL)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }
}
