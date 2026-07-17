package de.toengi.cili.dto.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFolderRequest(
        @NotBlank @Size(max = 255) String name,
        Long parentId,
        @Size(max = 5000) String description
) {}
