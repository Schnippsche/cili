package de.toengi.cili.dto.resource;

public record MetadataDto(
        String title,
        String description,
        String tags,
        String categories,
        String language
) {}
