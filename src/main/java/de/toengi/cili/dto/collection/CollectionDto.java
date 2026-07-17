package de.toengi.cili.dto.collection;

import java.time.LocalDateTime;

public record CollectionDto(
        Long id,
        String name,
        long itemCount,
        long testimonialCount,
        boolean isTemplate,
        LocalDateTime createdAt
) {}
