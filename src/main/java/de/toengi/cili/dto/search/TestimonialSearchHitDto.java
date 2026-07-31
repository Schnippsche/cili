package de.toengi.cili.dto.search;

import java.time.LocalDateTime;

public record TestimonialSearchHitDto(
    Long id,
    String authorName,
    String tags,
    String text,
    boolean human,
    boolean animal,
    LocalDateTime createdAt
) {}
