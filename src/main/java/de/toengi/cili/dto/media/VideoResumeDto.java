package de.toengi.cili.dto.media;

import java.time.LocalDateTime;

public record VideoResumeDto(
        Long resourceId,
        int positionSeconds,
        LocalDateTime updatedAt
) {}
