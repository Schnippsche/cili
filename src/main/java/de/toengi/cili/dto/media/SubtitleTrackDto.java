package de.toengi.cili.dto.media;

import de.toengi.cili.model.enums.SubtitleFormat;

import java.time.LocalDateTime;

public record SubtitleTrackDto(
        Long id,
        Long resourceId,
        String languageCode,
        String label,
        SubtitleFormat format,
        LocalDateTime createdAt,
        boolean hasTextContent
) {}
