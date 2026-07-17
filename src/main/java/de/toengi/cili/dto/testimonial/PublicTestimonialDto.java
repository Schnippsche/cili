package de.toengi.cili.dto.testimonial;

import java.time.LocalDateTime;
import java.util.List;

public record PublicTestimonialDto(
    Long id,
    String authorName,
    String tags,
    String text,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<TestimonialImageDto> images
) {}
