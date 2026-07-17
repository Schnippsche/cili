package de.toengi.cili.controller;

import de.toengi.cili.dto.collection.CollectionShareInfoDto;
import de.toengi.cili.dto.collection.CollectionShareTokenDto;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.CollectionShareService;
import de.toengi.cili.service.SubtitleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequiredArgsConstructor
public class CollectionShareController {

    private static final Logger log = LoggerFactory.getLogger(CollectionShareController.class);
    private final CollectionShareService service;

    // ── Public (no JWT) ──────────────────────────────────────────────────────

    @GetMapping("/api/share/collection/{token}/info")
    public CollectionShareInfoDto info(@PathVariable String token, HttpServletRequest request) {
        CollectionShareInfoDto info = service.getInfo(token);
        log.info("Collection share accessed: token={}, collection='{}', ip={}",
                token, info.collectionName(), extractIp(request));
        return info;
    }

    @GetMapping("/api/share/collection/{token}/stream/{resourceId}")
    public ResponseEntity<ResourceRegion> stream(
            @PathVariable String token,
            @PathVariable Long resourceId,
            @RequestHeader HttpHeaders headers) throws IOException {
        return service.streamResource(token, resourceId, headers);
    }

    @GetMapping(value = "/api/share/collection/{token}/thumbnail/{resourceId}",
                produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> thumbnail(
            @PathVariable String token,
            @PathVariable Long resourceId,
            @RequestParam(defaultValue = "small") String size) throws IOException {
        byte[] bytes = service.getThumbnail(token, resourceId, size);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=604800")
                .body(bytes);
    }

    @GetMapping("/api/share/collection/{token}/subtitles/{resourceId}/{trackId}/text")
    public ResponseEntity<String> subtitleText(
            @PathVariable String token,
            @PathVariable Long resourceId,
            @PathVariable Long trackId) {
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(service.getSubtitleText(token, resourceId, trackId));
    }

    @GetMapping("/api/share/collection/{token}/subtitles/{resourceId}/{trackId}")
    public ResponseEntity<StreamingResponseBody> subtitle(
            @PathVariable String token,
            @PathVariable Long resourceId,
            @PathVariable Long trackId) throws IOException {
        SubtitleService.SubtitleDownload result = service.getSubtitle(token, resourceId, trackId);
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

    // ── Protected (JWT required) ──────────────────────────────────────────────

    @PostMapping("/api/collections/{id}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionShareTokenDto create(
            @PathVariable Long id,
            @AuthenticationPrincipal CiliUserDetails user) {
        return service.createShare(user.getUserId(), id);
    }

    @GetMapping("/api/collections/{id}/share")
    public ResponseEntity<CollectionShareTokenDto> get(
            @PathVariable Long id,
            @AuthenticationPrincipal CiliUserDetails user) {
        return service.getShare(user.getUserId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/api/collections/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable Long id,
            @AuthenticationPrincipal CiliUserDetails user) {
        service.revokeShare(user.getUserId(), id);
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
