package de.toengi.cili.dto.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFolderRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 5000) String description
) {}
