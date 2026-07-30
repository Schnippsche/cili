package de.toengi.cili.dto.testimonial;

import java.time.LocalDateTime;

public record TestimonialAttachmentDto(
    Long id,           // Resource ID — für /api/resources/{id}/thumbnail
    String originalName,
    String mimeType,
    Long size,
    LocalDateTime createdAt,
    String thumbnailStatus,  // PENDING/DONE/FAILED, null wenn kein Thumbnail-Job existiert
    String storedName        // Cache-Busting-Parameter für die Thumbnail-URL sobald DONE
) {}
