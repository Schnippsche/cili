package de.toengi.cili.controller;

import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.service.TestimonialSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TestimonialSummaryController {

    private final TestimonialSummaryService summaryService;

    @PostMapping("/api/resources/{id}/testimonial-summary")
    public Map<String, Long> enqueue(@PathVariable Long id) {
        return Map.of("jobId", summaryService.enqueueSummary(id));
    }

    @GetMapping("/api/resources/{id}/testimonial-summary-jobs/active")
    public List<ProcessingJobDto> getActiveJobs(@PathVariable Long id) {
        return summaryService.getActiveJobs(id).stream()
                .map(ProcessingJobDto::from)
                .toList();
    }
}
