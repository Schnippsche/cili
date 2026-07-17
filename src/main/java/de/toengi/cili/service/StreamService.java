package de.toengi.cili.service;

import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StreamService {

    private static final long MAX_CHUNK = 10 * 1024 * 1024L; // 10 MB per chunk for open-end ranges
    public static final String BYTES = "bytes";

    private final ResourceRepository resourceRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public ResponseEntity<ResourceRegion> stream(Long resourceId, HttpHeaders headers, Long userId)
            throws IOException {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));

        Path path = storageService.resolveLocalPath(resource.getStoredName())
                .orElseThrow(() -> new IllegalStateException("Resource not stored locally"));

        FileSystemResource fileResource = new FileSystemResource(path);
        long contentLength = fileResource.contentLength();

        List<HttpRange> ranges = headers.getRange();
        ResourceRegion region;
        HttpStatus status;

        if (contentLength <= 0) {
            return ResponseEntity.noContent()
                    .header(HttpHeaders.ACCEPT_RANGES, BYTES)
                    .build();
        }

        return getResourceRegionResponseEntity(resource, fileResource, contentLength, ranges);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ResourceRegion> streamPublic(Long resourceId, HttpHeaders headers)
            throws IOException {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));

        Path path = storageService.resolveLocalPath(resource.getStoredName())
                .orElseThrow(() -> new IllegalStateException("Resource not stored locally"));

        FileSystemResource fileResource = new FileSystemResource(path);
        long contentLength = fileResource.contentLength();

        if (contentLength <= 0) {
            return ResponseEntity.noContent()
                    .header(HttpHeaders.ACCEPT_RANGES, BYTES)
                    .build();
        }

        List<HttpRange> ranges = headers.getRange();
        ResourceRegion region;
        HttpStatus status;

        return getResourceRegionResponseEntity(resource, fileResource, contentLength, ranges);
    }

    private ResponseEntity<ResourceRegion> getResourceRegionResponseEntity(Resource resource, FileSystemResource fileResource, long contentLength, List<HttpRange> ranges) {
        ResourceRegion region;
        HttpStatus status;
        if (ranges.isEmpty()) {
            long chunkSize = Math.min(MAX_CHUNK, contentLength);
            region = new ResourceRegion(fileResource, 0, chunkSize);
            status = HttpStatus.OK;
        } else {
            region = ranges.getFirst().toResourceRegion(fileResource);
            status = HttpStatus.PARTIAL_CONTENT;
        }

        String mimeStr = resource.getMimeType();
        MediaType contentType = (mimeStr != null && !mimeStr.isBlank())
                ? MediaType.parseMediaType(mimeStr)
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.status(status)
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, BYTES)
                .header("X-Content-Type-Options", "nosniff")
                .body(region);
    }
}
