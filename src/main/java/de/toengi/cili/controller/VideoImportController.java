package de.toengi.cili.controller;

import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.security.JwtTokenProvider;
import de.toengi.cili.service.VideoUrlImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/folders/{folderId}/video-import")
@RequiredArgsConstructor
public class VideoImportController {

    private static final long JOB_TOKEN_EXPIRY_MS = 4 * 60 * 60 * 1000L; // 4 Stunden

    private final VideoUrlImportService service;
    private final JwtTokenProvider jwtTokenProvider;

    public record VideoImportRequest(
        @NotBlank @Size(max = 2000) String url,
        @Size(max = 200)            String password
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasPermission(#folderId, 'FOLDER', 'UPLOAD')")
    public ProcessingJobDto trigger(
            @PathVariable long folderId,
            @Valid @RequestBody VideoImportRequest req,
            @AuthenticationPrincipal CiliUserDetails currentUser) {
        String jobToken = jwtTokenProvider.generateJobToken(currentUser, JOB_TOKEN_EXPIRY_MS);
        return ProcessingJobDto.from(
            service.submit(folderId, req.url(), req.password(), jobToken));
    }
}
