package de.toengi.cili.dto.user;

import java.time.LocalDateTime;

public record UserDto(
    Long id,
    String username,
    String email,
    String displayName,
    boolean active,
    String role,
    LocalDateTime createdAt
) {}
