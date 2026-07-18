package de.toengi.cili.dto.testimonial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateTestimonialRequest(
    @NotBlank @Size(max = 200) String authorName,
    @Size(max = 500) String tags,
    @NotBlank @Size(min = 10, max = 50000) String text,
    @NotBlank @Pattern(regexp = "Mensch|Tier", message = "source muss 'Mensch' oder 'Tier' sein") String source,
    LocalDateTime createdAt
) {}
