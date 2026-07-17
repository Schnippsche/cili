package de.toengi.cili.service;

import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.ResourceMetadata;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class TextEditorService {

    private final ResourceRepository resourceRepository;
    private final ResourceMetadataRepository metadataRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public String getContent(Long resourceId, Long userId) {
        Resource resource = findOrThrow(resourceId);
        try (InputStream in = storageService.retrieve(resource.getStoredName())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'WRITE')")
    public void saveContent(Long resourceId, String content, Long userId) {
        Resource resource = findOrThrow(resourceId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try {
            storageService.update(resource.getStoredName(), new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ResourceMetadata meta = metadataRepository.findByResourceId(resourceId)
                .orElseGet(() -> ResourceMetadata.builder().resourceId(resourceId).build());
        meta.setTextContent(content);
        metadataRepository.save(meta);
    }

    private Resource findOrThrow(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));
    }
}
