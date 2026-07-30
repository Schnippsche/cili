package de.toengi.cili.dto.upload;

import jakarta.validation.constraints.*;

public record InitUploadRequest(
        @NotBlank @Size(max = 500) String fileName,
        @NotBlank @Size(max = 200) String mimeType,
        @Positive long totalSize,
        @Min(1) @Max(104857600) int chunkSize,
        Long folderId,
        Long testimonialId,
        Long fileLastModified,
        Long bulkImportItemId
) {}
