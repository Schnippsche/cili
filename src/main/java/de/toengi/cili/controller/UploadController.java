package de.toengi.cili.controller;

import de.toengi.cili.dto.upload.CompleteUploadResponse;
import de.toengi.cili.dto.upload.InitUploadRequest;
import de.toengi.cili.dto.upload.UploadJobDto;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.UploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/init")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("#request.folderId() == null or hasPermission(#request.folderId(), 'FOLDER', 'UPLOAD')")
    public UploadJobDto initUpload(@Valid @RequestBody InitUploadRequest request,
                                   @AuthenticationPrincipal CiliUserDetails user) {
        log.info("[user:{}] Upload gestartet: \"{}\" ({} MB) → Ordner {}",
            user.getUsername(), request.fileName(), request.totalSize() / 1024 / 1024, request.folderId());
        return uploadService.initUpload(request, user.getUserId());
    }

    @PutMapping(value = "/{jobId}/chunk/{chunkIndex}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadJobDto uploadChunk(@PathVariable String jobId,
                                    @PathVariable int chunkIndex,
                                    InputStream requestBody,
                                    @AuthenticationPrincipal CiliUserDetails user) {
        return uploadService.uploadChunk(jobId, chunkIndex, requestBody, user.getUserId());
    }

    @PostMapping("/{jobId}/complete")
    public CompleteUploadResponse completeUpload(@PathVariable String jobId,
                                                 @RequestParam(defaultValue = "false") boolean pinToTop,
                                                 @AuthenticationPrincipal CiliUserDetails user) {
        CompleteUploadResponse resp = uploadService.completeUpload(jobId, user.getUserId(), pinToTop);
        log.info("[user:{}] Upload abgeschlossen: Resource-ID {}", user.getUsername(), resp.resourceId());
        return resp;
    }

    @GetMapping("/{jobId}/status")
    public UploadJobDto getStatus(@PathVariable String jobId,
                                  @AuthenticationPrincipal CiliUserDetails user) {
        return uploadService.getStatus(jobId, user.getUserId());
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelUpload(@PathVariable String jobId,
                             @AuthenticationPrincipal CiliUserDetails user) {
        uploadService.cancelUpload(jobId, user.getUserId());
    }
}
