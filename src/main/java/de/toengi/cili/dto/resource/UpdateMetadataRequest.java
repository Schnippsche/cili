package de.toengi.cili.dto.resource;

import jakarta.validation.constraints.Size;

public record UpdateMetadataRequest(
        @Size(max = 500) String title,
        String description,
        String tags,
        String categories,
        @Size(max = 50) String language
) {}
