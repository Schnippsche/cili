package de.toengi.cili.dto.testimonial;

import java.time.LocalDateTime;

public record TestimonialAttachmentDto(
    Long id,           // Resource ID — für /api/resources/{id}/thumbnail
    String originalName,
    String mimeType,
    Long size,
    LocalDateTime createdAt
) {}
