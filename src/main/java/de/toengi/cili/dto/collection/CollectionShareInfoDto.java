package de.toengi.cili.dto.collection;

import de.toengi.cili.dto.testimonial.PublicTestimonialDto;

import java.time.LocalDateTime;
import java.util.List;

public record CollectionShareInfoDto(
        String collectionName,
        LocalDateTime expiresAt,
        List<SharedResourceItemDto> resources,
        List<PublicTestimonialDto> testimonials
) {}
