package de.toengi.cili.dto.group;

import java.time.LocalDateTime;

public record GroupDto(
    Long id,
    String name,
    String description,
    boolean system,
    int memberCount,
    LocalDateTime createdAt
) {}
