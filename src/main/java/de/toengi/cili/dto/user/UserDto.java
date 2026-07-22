package de.toengi.cili.dto.user;

import java.time.LocalDateTime;

public record UserDto(
    Long id,
    String username,
    String email,
    String displayName,
    Integer memberId,
    String url,
    String phone,
    boolean active,
    String role,
    LocalDateTime createdAt
) {}
