package de.toengi.cili.service;

import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ResourceMetadata;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TextEditorServiceTest {

    @Mock ResourceRepository resourceRepository;
    @Mock ResourceMetadataRepository metadataRepository;
    @Mock StorageService storageService;

    @InjectMocks TextEditorService textEditorService;

    @Test
    void getContent_returnsTextFromStorage() throws IOException {
        Resource r = resource(1L, "abc123");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(r));
        when(storageService.retrieve("abc123"))
                .thenReturn(new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8)));

        String content = textEditorService.getContent(1L, 99L);

        assertThat(content).isEqualTo("hello world");
    }

    @Test
    void getContent_resourceNotFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> textEditorService.getContent(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveContent_updatesStorageAndTextContent() throws IOException {
        Resource r = resource(2L, "def456");
        when(resourceRepository.findById(2L)).thenReturn(Optional.of(r));
        when(metadataRepository.findByResourceId(2L)).thenReturn(Optional.empty());
        when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        textEditorService.saveContent(2L, "new content", 1L);

        verify(storageService).update(eq("def456"), any());
        verify(metadataRepository).save(argThat(m -> "new content".equals(m.getTextContent())));
    }

    @Test
    void saveContent_updatesExistingMetadata() throws IOException {
        Resource r = resource(3L, "ghi789");
        ResourceMetadata existing = ResourceMetadata.builder().resourceId(3L).textContent("old").build();
        when(resourceRepository.findById(3L)).thenReturn(Optional.of(r));
        when(metadataRepository.findByResourceId(3L)).thenReturn(Optional.of(existing));
        when(metadataRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        textEditorService.saveContent(3L, "updated content", 1L);

        verify(metadataRepository).save(argThat(m -> "updated content".equals(m.getTextContent())));
    }

    @Test
    void saveContent_resourceNotFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> textEditorService.saveContent(99L, "x", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Resource resource(Long id, String storedName) {
        return Resource.builder()
                .id(id).folderId(10L).originalName("doc.txt")
                .storedName(storedName).mimeType("text/plain")
                .size(50L).uploaderId(99L).storageType(StorageType.LOCAL)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
