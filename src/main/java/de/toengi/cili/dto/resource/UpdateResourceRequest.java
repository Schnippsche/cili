package de.toengi.cili.dto.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateResourceRequest(
        @NotBlank @Size(max = 500) String originalName
) {}
