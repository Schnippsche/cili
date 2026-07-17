package de.toengi.cili.dto.search;

import java.util.List;

public record SearchResponse(
    List<SearchHitDto> hits,
    long totalHits,
    int page,
    int size,
    List<TestimonialSearchHitDto> testimonialHits
) {}
