package de.toengi.cili.controller;

import de.toengi.cili.dto.media.SubtitleTrackDto;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.SubtitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;


@RestController
@RequiredArgsConstructor
public class SubtitleController {

    private final SubtitleService subtitleService;

    @GetMapping("/api/resources/{id}/subtitles")
    public List<SubtitleTrackDto> list(@PathVariable Long id,
                                       @AuthenticationPrincipal CiliUserDetails user) {
        return subtitleService.list(id, user.getUserId());
    }

    @PostMapping("/api/resources/{id}/subtitles")
    @ResponseStatus(HttpStatus.CREATED)
    public SubtitleTrackDto upload(@PathVariable Long id,
                                   @RequestParam String languageCode,
                                   @RequestParam(required = false) String label,
                                   @RequestParam String format,
                                   @RequestParam("file") MultipartFile file,
                                   @AuthenticationPrincipal CiliUserDetails user) throws IOException {
        SubtitleFormat fmt;
        try {
            fmt = SubtitleFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown subtitle format: " + format);
        }
        return subtitleService.upload(id, languageCode, label, fmt,
                file.getBytes(), user.getUserId());
    }

    @GetMapping("/api/resources/{id}/subtitles/{trackId}/text")
    public ResponseEntity<String> getText(@PathVariable Long id,
                                          @PathVariable Long trackId,
                                          @AuthenticationPrincipal CiliUserDetails user) {
        String text = subtitleService.getText(id, trackId, user.getUserId());
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(text);
    }

    @GetMapping("/api/resources/{id}/subtitles/{trackId}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long id,
                                                          @PathVariable Long trackId,
                                                          @AuthenticationPrincipal CiliUserDetails user)
            throws IOException {
        SubtitleService.SubtitleDownload result = subtitleService.download(id, trackId, user.getUserId());
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

    @GetMapping("/api/resources/{id}/subtitles/{trackId}/download")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @PathVariable Long trackId,
                                         @RequestParam(defaultValue = "vtt") String format,
                                         @AuthenticationPrincipal CiliUserDetails user) throws IOException {
        SubtitleService.SubtitleExport result = subtitleService.export(id, trackId, format, user.getUserId());
        String disposition = "attachment; filename=\"" + result.filename() + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CONTENT_TYPE, result.contentType() + "; charset=UTF-8")
                .body(result.bytes());
    }

    @DeleteMapping("/api/resources/{id}/subtitles/{trackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @PathVariable Long trackId,
                       @AuthenticationPrincipal CiliUserDetails user) throws IOException {
        subtitleService.delete(id, trackId, user.getUserId());
    }

    @PostMapping("/api/resources/{id}/retranscribe")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retranscribe(@PathVariable Long id,
                             @AuthenticationPrincipal CiliUserDetails user) {
        subtitleService.retranscribe(id, user.getUserId());
    }

    @GetMapping("/api/resources/{id}/transcription-jobs/active")
    public boolean hasActiveTranscriptionJob(@PathVariable Long id,
                                             @AuthenticationPrincipal CiliUserDetails user) {
        return subtitleService.hasActiveTranscriptionJob(id, user.getUserId());
    }
}
