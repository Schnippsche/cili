package de.toengi.cili.dto.report;

import java.time.LocalDateTime;
import java.util.List;

public record ReportTestimonialDto(
    Long id,
    String authorName,
    String tags,
    String text,
    LocalDateTime createdAt,
    List<ReportImageDto> images
) {}
