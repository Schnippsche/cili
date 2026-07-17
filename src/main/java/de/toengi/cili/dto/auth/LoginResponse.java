package de.toengi.cili.dto.auth;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserInfo user
) {
    public record UserInfo(Long id, String username, String displayName, String role) {}
}
