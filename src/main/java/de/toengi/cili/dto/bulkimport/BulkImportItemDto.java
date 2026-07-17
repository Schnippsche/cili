package de.toengi.cili.dto.bulkimport;

import de.toengi.cili.model.enums.BulkImportItemStatus;

public record BulkImportItemDto(
        Long id,
        String relativePath,
        Long resolvedFolderId,
        BulkImportItemStatus status,
        String skipReason,
        String errorMessage,
        Long resourceId
) {}
