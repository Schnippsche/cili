package de.toengi.cili.config;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "cili.jwt")
@Getter @Setter
public class JwtConfig {
    private String secret;
    private long accessTokenExpiry = 900_000L;
    private long refreshTokenExpiry = 604_800_000L;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }
}
