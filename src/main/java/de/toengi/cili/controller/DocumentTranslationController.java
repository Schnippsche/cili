package de.toengi.cili.controller;

import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.dto.translation.DocumentTranslationRequest;
import de.toengi.cili.service.DocumentTranslationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class DocumentTranslationController {

    private final DocumentTranslationService documentTranslationService;

    public DocumentTranslationController(DocumentTranslationService documentTranslationService) {
        this.documentTranslationService = documentTranslationService;
    }

    @PostMapping("/api/resources/{id}/document-translations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Long> requestTranslation(
            @PathVariable Long id,
            @Valid @RequestBody DocumentTranslationRequest req) {
        return documentTranslationService.enqueueTranslation(id, req);
    }

    @GetMapping("/api/resources/{id}/document-translations/active")
    public List<ProcessingJobDto> getActiveJobs(@PathVariable Long id) {
        return documentTranslationService.getActiveJobs(id);
    }
}
