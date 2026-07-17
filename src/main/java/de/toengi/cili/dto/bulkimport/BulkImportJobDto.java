package de.toengi.cili.dto.bulkimport;

import java.util.List;

public record BulkImportJobDto(
        String jobId,
        String rootName,
        Long targetFolderId,
        String status,
        int filesTotal,
        int filesDone,
        int filesSkipped,
        int filesFailed,
        List<BulkImportItemDto> items
) {}
