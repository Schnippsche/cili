package de.toengi.cili.dto.bulkimport;

import java.util.List;

public record CreateBulkImportResponse(String jobId, List<BulkImportItemDto> items) {}
