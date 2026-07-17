package de.toengi.cili.service;

import de.toengi.cili.config.OllamaIdleConfig;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class OllamaIdleScheduler {

    private static final List<ProcessingJobType> BLOCKING_JOB_TYPES = List.of(
            ProcessingJobType.OLLAMA_ANALYSIS,
            ProcessingJobType.WHISPER_TRANSCRIBE,
            ProcessingJobType.SUBTITLE_TRANSLATE,
            ProcessingJobType.DOCUMENT_TRANSLATE
    );

    private final OllamaIdleConfig config;
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final OllamaAnalysisService ollamaAnalysisService;
    private final ProcessingJobRepository jobRepository;

    public OllamaIdleScheduler(
            OllamaIdleConfig config,
            SubtitleTrackRepository subtitleTrackRepository,
            OllamaAnalysisService ollamaAnalysisService,
            ProcessingJobRepository jobRepository) {
        this.config = config;
        this.subtitleTrackRepository = subtitleTrackRepository;
        this.ollamaAnalysisService = ollamaAnalysisService;
        this.jobRepository = jobRepository;
    }

    @Scheduled(fixedDelayString = "${cili.ollama.idle-backfill.interval:PT10M}")
    public void backfillIfIdle() {
        if (!config.isEnabled()) return;

        if (jobRepository.existsActiveJobOfTypes(BLOCKING_JOB_TYPES)) {
            log.debug("GPU-Job aktiv (Ollama/Whisper/NLLB) — Idle-Backfill übersprungen");
            return;
        }

        subtitleTrackRepository
            .findOldestResourceMissingSummary(config.getLanguage())
            .ifPresentOrElse(
                resourceId -> {
                    log.info("Idle-Backfill: starte Ollama-Analyse resourceId={} lang={}",
                        resourceId, config.getLanguage());
                    try {
                        ollamaAnalysisService.enqueueAsSystem(resourceId, config.getLanguage());
                    } catch (Exception e) {
                        log.error("Idle-Backfill: Fehler beim Enqueuing resourceId={}: {}",
                            resourceId, e.getMessage(), e);
                    }
                },
                () -> log.debug("Idle-Backfill: keine ausstehenden Videos gefunden")
            );
    }
}
