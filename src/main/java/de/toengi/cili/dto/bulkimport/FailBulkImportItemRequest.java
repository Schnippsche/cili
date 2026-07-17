package de.toengi.cili.dto.bulkimport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailBulkImportItemRequest(@NotBlank @Size(max = 500) String errorMessage) {}
