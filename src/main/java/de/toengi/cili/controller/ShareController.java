package de.toengi.cili.controller;

import de.toengi.cili.dto.share.ShareInfoDto;
import de.toengi.cili.dto.share.ShareTokenDto;
import java.util.Map;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.ShareService;
import de.toengi.cili.service.SubtitleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final ShareService shareService;

    // ── Public (no JWT) ──────────────────────────────────────────────────────

    @GetMapping("/api/share/{token}")
    public ResponseEntity<ResourceRegion> stream(@PathVariable String token,
                                                  @RequestHeader HttpHeaders headers,
                                                  HttpServletRequest request)
            throws IOException {
        // Log only the initial request, not subsequent range requests
        if (headers.getRange().isEmpty()) {
            log.info("Share link streamed: token={}, ip={}", token, extractIp(request));
        }
        return shareService.streamByToken(token, headers);
    }

    @GetMapping("/api/share/{token}/info")
    public ShareInfoDto info(@PathVariable String token, HttpServletRequest request) {
        ShareInfoDto info = shareService.getShareInfo(token);
        log.info("Share link accessed: token={}, resource='{}', ip={}", token, info.originalName(), extractIp(request));
        return info;
    }

    @GetMapping("/api/share/{token}/subtitles/{trackId}/text")
    public ResponseEntity<String> subtitleText(@PathVariable String token,
                                               @PathVariable Long trackId) {
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(shareService.getSubtitleText(token, trackId));
    }

    @GetMapping("/api/share/{token}/subtitles/{trackId}")
    public ResponseEntity<StreamingResponseBody> subtitle(@PathVariable String token,
                                                          @PathVariable Long trackId)
            throws IOException {
        SubtitleService.SubtitleDownload result = shareService.getSubtitleDownload(token, trackId);
        StreamingResponseBody body = out -> {
            try (InputStream stream = result.stream()) {
                stream.transferTo(out);
            }
        };
        String contentType = result.format() == SubtitleFormat.VTT ? "text/vtt" : "text/plain";
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(body);
    }

    @GetMapping("/api/share/config")
    public Map<String, Object> config() {
        Map<String, Object> cfg = new java.util.LinkedHashMap<>();
        cfg.put("validityDays", shareService.getValidityDays());
        String baseUrl = shareService.getShareBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            cfg.put("baseUrl", baseUrl);
        }
        return cfg;
    }

    // ── Protected (login + SHARE permission via @PreAuthorize in service) ────

    @PostMapping("/api/resources/{id}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareTokenDto create(@PathVariable Long id,
                                 @AuthenticationPrincipal CiliUserDetails user) {
        return shareService.createShare(id, user.getUserId());
    }

    @GetMapping("/api/resources/{id}/share")
    public ResponseEntity<ShareTokenDto> get(@PathVariable Long id,
                                              @AuthenticationPrincipal CiliUserDetails user) {
        return shareService.getShare(id, user.getUserId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/api/resources/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id,
                        @AuthenticationPrincipal CiliUserDetails user) {
        shareService.revokeShare(id, user.getUserId());
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
