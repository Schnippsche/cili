package de.toengi.cili.controller;

import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.security.CiliUserDetails;
import de.toengi.cili.service.VideoClipService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class VideoClipController {

    private final VideoClipService clipService;

    public record CreateClipRequest(long startMs, long endMs, String title) {}

    @PostMapping("/api/resources/{id}/clip")
    public Map<String, Long> createClip(
            @PathVariable Long id,
            @RequestBody CreateClipRequest request,
            @AuthenticationPrincipal CiliUserDetails user) {
        long jobId = clipService.enqueueClip(
                id, request.startMs(), request.endMs(), request.title(), user.getUserId());
        return Map.of("jobId", jobId);
    }

    @GetMapping("/api/resources/{id}/clip-jobs/active")
    public List<ProcessingJobDto> getActiveClipJobs(@PathVariable Long id) {
        return clipService.getActiveClipJobs(id).stream()
                .map(ProcessingJobDto::from)
                .toList();
    }
}
