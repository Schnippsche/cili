package de.toengi.cili.dto.testimonial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTestimonialRequest(
    @NotBlank @Size(max = 200) String authorName,
    @Size(max = 500) String tags,
    @NotBlank @Size(min = 10, max = 50000) String text
) {}
