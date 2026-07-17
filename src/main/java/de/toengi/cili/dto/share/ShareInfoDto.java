package de.toengi.cili.dto.share;

import de.toengi.cili.dto.media.SubtitleTrackDto;

import java.util.List;

public record ShareInfoDto(String originalName, String mimeType, List<SubtitleTrackDto> subtitles) {}
