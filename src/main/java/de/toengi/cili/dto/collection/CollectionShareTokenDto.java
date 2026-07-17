package de.toengi.cili.dto.collection;

import java.time.LocalDateTime;

public record CollectionShareTokenDto(
        Long collectionId,
        String token,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        int validityDays
) {}
