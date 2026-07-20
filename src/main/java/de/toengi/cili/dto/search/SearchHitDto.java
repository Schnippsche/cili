package de.toengi.cili.dto.search;

import java.time.LocalDateTime;
import java.util.List;

public record SearchHitDto(
    Long resourceId,
    String name,
    String title,
    String mimeType,
    Long size,
    Long folderId,
    String folderPath,
    LocalDateTime uploadedAt,
    float score,
    List<SnippetDto> snippets,
    String thumbnailStatus,
    String storedName
) {}
