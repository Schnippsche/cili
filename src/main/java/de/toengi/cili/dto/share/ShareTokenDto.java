package de.toengi.cili.dto.share;

import java.time.LocalDateTime;

public record ShareTokenDto(Long resourceId, String token, LocalDateTime createdAt, LocalDateTime expiresAt, int validityDays) {}
