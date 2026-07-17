package de.toengi.cili.controller;

import de.toengi.cili.dto.auth.ChangePasswordRequest;
import de.toengi.cili.dto.auth.LoginRequest;
import de.toengi.cili.dto.auth.LoginResponse;
import de.toengi.cili.dto.auth.RefreshRequest;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, extractClientIp(httpRequest)));
    }

    private static String extractClientIp(HttpServletRequest request) {
        // Cloudflare Tunnel setzt CF-Connecting-IP auf die echte Client-IP
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip != null && !ip.isBlank()) return ip;
        ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               @AuthenticationPrincipal CiliUserDetails user) {
        authService.changePassword(request.currentPassword(), request.newPassword(), user);
        return ResponseEntity.noContent().build();
    }
}
