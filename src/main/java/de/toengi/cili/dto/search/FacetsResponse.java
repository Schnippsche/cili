package de.toengi.cili.dto.search;

import java.util.List;

public record FacetsResponse(
    List<FacetDto> mimeTypes,
    List<FacetDto> languages
) {}
