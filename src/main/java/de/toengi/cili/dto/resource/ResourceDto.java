package de.toengi.cili.dto.resource;

import de.toengi.cili.model.enums.StorageType;
import java.time.LocalDateTime;

public record ResourceDto(
        Long id,
        Long folderId,
        String originalName,
        String storedName,
        String mimeType,
        Long size,
        String checksum,
        Long uploaderId,
        StorageType storageType,
        LocalDateTime fileDate,
        Long sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        MetadataDto metadata,
        String thumbnailStatus,
        boolean hasAnalyzableSubtitles
) {}
