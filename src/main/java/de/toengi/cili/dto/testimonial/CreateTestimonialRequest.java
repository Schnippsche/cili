package de.toengi.cili.dto.testimonial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateTestimonialRequest(
    @NotBlank @Size(max = 200) String authorName,
    @Size(max = 500) String tags,
    @NotBlank @Size(min = 10, max = 50000) String text,
    boolean human,
    boolean animal,
    LocalDateTime createdAt
) {}
