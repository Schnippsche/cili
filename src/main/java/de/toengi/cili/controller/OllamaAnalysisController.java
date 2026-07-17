package de.toengi.cili.controller;

import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.dto.resource.AiSummaryDto;
import de.toengi.cili.service.OllamaAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OllamaAnalysisController {

    private final OllamaAnalysisService analysisService;

    @GetMapping("/api/resources/{id}/summaries")
    public List<AiSummaryDto> getSummaries(@PathVariable Long id) {
        return analysisService.getAllSummaries(id);
    }

    @PostMapping("/api/resources/{id}/analyze")
    public Map<String, Long> enqueue(
            @PathVariable Long id,
            @RequestParam String languageCode,
            @RequestParam(defaultValue = "false") boolean force) {
        long jobId = analysisService.enqueueAnalysis(id, languageCode, force);
        return Map.of("jobId", jobId);
    }

    @GetMapping("/api/resources/{id}/analysis-jobs/active")
    public List<ProcessingJobDto> getActiveJobs(@PathVariable Long id) {
        return analysisService.getActiveJobs(id).stream()
                .map(ProcessingJobDto::from)
                .toList();
    }
}
