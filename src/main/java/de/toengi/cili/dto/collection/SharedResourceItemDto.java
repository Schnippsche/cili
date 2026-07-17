package de.toengi.cili.dto.collection;

import de.toengi.cili.dto.media.SubtitleTrackDto;

import java.util.List;

public record SharedResourceItemDto(
        Long id,
        String originalName,
        String mimeType,
        boolean hasThumbnail,
        List<SubtitleTrackDto> subtitles
) {}
