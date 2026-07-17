package de.toengi.cili.dto.bulkimport;

import jakarta.validation.constraints.*;

public record BulkImportEntry(
        @NotBlank @Size(max = 1000) String relativePath,
        @PositiveOrZero long fileSize,
        @Size(max = 200) String mimeType,
        Long fileLastModified
) {}
