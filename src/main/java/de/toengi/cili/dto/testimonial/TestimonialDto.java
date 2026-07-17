package de.toengi.cili.dto.testimonial;

import java.time.LocalDateTime;
import java.util.List;

public record TestimonialDto(
    Long id,
    String authorName,
    String tags,
    String text,
    Long userId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<TestimonialImageDto> images
) {}
