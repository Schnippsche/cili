package de.toengi.cili.dto.bulkimport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBulkImportRequest(
        @NotNull Long targetFolderId,
        @NotBlank @Size(max = 255) String rootName,
        @NotEmpty @Valid List<BulkImportEntry> entries
) {}
