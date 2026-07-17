package de.toengi.cili.controller;

import de.toengi.cili.dto.media.VideoResumeDto;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.VideoResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class VideoResumeController {

    private final VideoResumeService videoResumeService;

    @GetMapping("/api/resources/{id}/resume")
    public VideoResumeDto getResume(@PathVariable Long id,
                                     @AuthenticationPrincipal CiliUserDetails user) {
        return videoResumeService.getResume(id, user.getUserId());
    }

    @PutMapping("/api/resources/{id}/resume")
    public VideoResumeDto saveResume(@PathVariable Long id,
                                      @RequestBody VideoResumeDto request,
                                      @AuthenticationPrincipal CiliUserDetails user) {
        return videoResumeService.saveResume(id, request.positionSeconds(), user.getUserId());
    }
}
