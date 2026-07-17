package de.toengi.cili.controller;

import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.dto.translation.SubtitleTranslationRequest;
import de.toengi.cili.service.SubtitleTranslationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SubtitleTranslationController {

    private final SubtitleTranslationService translationService;

    public SubtitleTranslationController(SubtitleTranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping("/api/resources/{id}/subtitle-translations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Long> requestTranslation(
            @PathVariable Long id,
            @Valid @RequestBody SubtitleTranslationRequest req,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        return translationService.enqueueTranslation(id, req, overwrite);
    }

    @GetMapping("/api/resources/{id}/subtitle-translations/active")
    public List<ProcessingJobDto> getActiveJobs(@PathVariable Long id) {
        return translationService.getActiveJobs(id);
    }
}
