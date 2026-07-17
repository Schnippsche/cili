package de.toengi.cili.dto.folder;

import java.time.LocalDateTime;

public record FolderDto(
        Long id,
        String name,
        Long parentId,
        String path,
        String description,
        boolean trashed,
        LocalDateTime trashedAt,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
