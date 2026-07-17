package de.toengi.cili.dto.resource;

import java.time.Instant;

public record AiSummaryDto(String languageCode, String summary, Instant createdAt) {}
