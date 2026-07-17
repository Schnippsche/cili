package de.toengi.cili.controller;

import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.DocumentPreviewService;
import de.toengi.cili.service.StreamService;
import de.toengi.cili.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    private final StreamService streamService;
    private final ThumbnailService thumbnailService;
    private final DocumentPreviewService documentPreviewService;

    @GetMapping("/api/resources/{id}/stream")
    public ResponseEntity<ResourceRegion> stream(@PathVariable Long id,
                                                  @RequestHeader HttpHeaders headers,
                                                  @AuthenticationPrincipal CiliUserDetails user)
            throws IOException {
        boolean isFirstRequest = headers.getRange().isEmpty()
            || headers.getRange().get(0).getRangeStart(Long.MAX_VALUE) == 0;
        if (isFirstRequest) {
            log.info("[user:{}] Video/Audio geöffnet: id={}", user.getUsername(), id);
        }
        return streamService.stream(id, headers, user.getUserId());
    }

    @GetMapping(value = "/api/resources/{id}/thumbnail", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> thumbnail(@PathVariable Long id,
                                             @RequestParam(defaultValue = "small") String size)
            throws IOException {
        byte[] bytes = thumbnailService.getThumbnailBytes(id, size);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=604800")
                .body(bytes);
    }

    @GetMapping("/api/resources/{id}/preview")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable Long id,
                                                          @AuthenticationPrincipal CiliUserDetails user)
            throws IOException {
        log.info("[user:{}] Dokument vorgeschaut: id={}", user.getUsername(), id);
        Path pdfPath = documentPreviewService.getPreviewPath(id, user.getUserId());
        StreamingResponseBody body = out -> Files.copy(pdfPath, out);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(Files.size(pdfPath))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(body);
    }
}
