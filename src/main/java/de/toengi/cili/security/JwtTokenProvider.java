package de.toengi.cili.security;

import de.toengi.cili.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;

    public String generateAccessToken(CiliUserDetails userDetails) {
        return generateToken(userDetails, jwtConfig.getAccessTokenExpiry());
    }

    public String generateJobToken(CiliUserDetails userDetails, long expiryMs) {
        return generateToken(userDetails, expiryMs);
    }

    private String generateToken(CiliUserDetails userDetails, long expiryMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("userId", userDetails.getUserId())
                .claim("role", userDetails.getRole().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(jwtConfig.getSecretKey())
                .compact();
    }

    public String generateRefreshTokenRaw() {
        return UUID.randomUUID().toString();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtConfig.getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
