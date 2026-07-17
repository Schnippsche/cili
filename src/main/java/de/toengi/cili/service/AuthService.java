package de.toengi.cili.service;

import de.toengi.cili.config.JwtConfig;
import de.toengi.cili.dto.auth.LoginRequest;
import de.toengi.cili.dto.auth.LoginResponse;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.model.entity.RefreshToken;
import de.toengi.cili.model.entity.User;
import de.toengi.cili.repository.RefreshTokenRepository;
import de.toengi.cili.repository.UserRepository;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
import de.toengi.cili.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider tokenProvider;
    private final JwtConfig jwtConfig;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.username();
        if (loginAttemptService.isBlocked(clientIp, username)) {
            log.warn("Login blockiert (zu viele Fehlversuche): ip={} user='{}'", clientIp, username);
            throw new CiliException("Zu viele Fehlversuche. Bitte später erneut versuchen.", HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
            if (!(auth.getPrincipal() instanceof CiliUserDetails userDetails)) {
                throw new CiliException("Invalid credentials", HttpStatus.UNAUTHORIZED);
            }
            loginAttemptService.loginSucceeded(clientIp, username);
            log.info("Login: user='{}' (id={}) ip={}", userDetails.getUsername(), userDetails.getUser().getId(), clientIp);
            return buildLoginResponse(userDetails);
        } catch (AuthenticationException e) {
            loginAttemptService.loginFailed(clientIp, username);
            log.warn("Login fehlgeschlagen: user='{}' ip={}", username, clientIp);
            throw new CiliException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new CiliException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (!stored.isValid()) {
            throw new CiliException("Refresh token expired or revoked", HttpStatus.UNAUTHORIZED);
        }

        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        var userDetails = new CiliUserDetails(user);
        return buildLoginResponse(userDetails);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword, CiliUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getUser().getId())
                .orElseThrow(() -> new CiliException("User not found", HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CiliException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
            t.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(t);
            log.info("Logout: user='{}' (id={})", t.getUser().getUsername(), t.getUser().getId());
        });
    }

    private LoginResponse buildLoginResponse(CiliUserDetails userDetails) {
        String accessToken = tokenProvider.generateAccessToken(userDetails);
        String rawRefresh = tokenProvider.generateRefreshTokenRaw();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(userDetails.getUser())
                .tokenHash(sha256(rawRefresh))
                .expiresAt(LocalDateTime.now().plusSeconds(jwtConfig.getRefreshTokenExpiry() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        User user = userDetails.getUser();
        return new LoginResponse(
                accessToken,
                rawRefresh,
                jwtConfig.getAccessTokenExpiry() / 1000,
                new LoginResponse.UserInfo(user.getId(), user.getUsername(),
                        user.getDisplayName(), user.getRole().name())
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
