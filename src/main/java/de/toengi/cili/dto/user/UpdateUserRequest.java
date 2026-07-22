package de.toengi.cili.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Email String email,
    @Size(min = 8) String password,
    String displayName,
    Integer memberId,
    String url,
    String phone,
    Boolean active,
    String role
) {}
