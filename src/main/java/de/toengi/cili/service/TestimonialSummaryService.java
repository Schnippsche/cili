package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.OllamaConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class TestimonialSummaryService {

    private static final String PROMPT_FILE = "testimonial_summary_prompt.txt";
    private static final List<ProcessingJobStatus> ACTIVE =
            List.of(ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING);

    private final SubtitleTrackRepository subtitleTrackRepository;
    private final ProcessingJobRepository jobRepository;
    private final ProcessingJobService jobService;
    private final OllamaConfig config;
    private final OllamaScriptRunner scriptRunner;
    private final Executor gpuExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TestimonialSummaryService(
            SubtitleTrackRepository subtitleTrackRepository,
            ProcessingJobRepository jobRepository,
            ProcessingJobService jobService,
            OllamaConfig config,
            OllamaScriptRunner scriptRunner,
            @Qualifier("gpuExecutor") Executor gpuExecutor) {
        this.subtitleTrackRepository = subtitleTrackRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.config = config;
        this.scriptRunner = scriptRunner;
        this.gpuExecutor = gpuExecutor;
    }

    /** Stellt einen Testimonial-Zusammenfassungs-Job in die Warteschlange und gibt die Job-ID zurück. */
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public long enqueueSummary(Long resourceId) {
        // Jobs mit gesetzter errorMessage sind bereits mindestens einmal fehlgeschlagen und warten
        // (Status bleibt PENDING, siehe ProcessingJobService.markFailed) auf den nächsten automatischen
        // Retry — das kann bis zu zombie-timeout-minutes dauern. Ein manueller Klick auf "Erneut
        // versuchen" soll sofort einen frischen Versuch anstoßen statt nur die alte, stecken gebliebene
        // Job-ID zurückzugeben (sonst würde der Button ewig als "läuft noch" erscheinen).
        Optional<ProcessingJob> dup = jobRepository.findByResourceIdAndTypeAndStatusIn(
                        resourceId, ProcessingJobType.TESTIMONIAL_SUMMARY, ACTIVE)
                .stream()
                .filter(j -> j.getErrorMessage() == null)
                .findFirst();
        if (dup.isPresent()) {
            log.info("TESTIMONIAL_SUMMARY für resource {} bereits aktiv — Job {} zurückgegeben",
                    resourceId, dup.get().getId());
            return dup.get().getId();
        }
        // maxAttempts=1: einmalige, nutzerausgelöste Aktion — ein Fehlschlag soll sofort als FAILED
        // sichtbar sein (Button/Jobliste), statt still auf PENDING zu bleiben und erst beim nächsten
        // Server-Neustart oder Zombie-Timeout automatisch erneut zu laufen.
        ProcessingJob job = jobService.createJob(resourceId, ProcessingJobType.TESTIMONIAL_SUMMARY, null, null, 1);
        gpuExecutor.execute(() -> execute(job));
        return job.getId();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public List<ProcessingJob> getActiveJobs(Long resourceId) {
        return jobRepository.findActiveTestimonialSummaryJobs(resourceId, LocalDateTime.now().minusHours(2));
    }

    /** Wird vom gpuExecutor und vom JobRecoveryService aufgerufen. */
    public void execute(ProcessingJob job) {
        jobService.markRunning(job, "testimonial-summary:" + Thread.currentThread().getName());
        try {
            Long resourceId = job.getResourceId();
            List<SubtitleTrack> withContent = subtitleTrackRepository.findByResourceId(resourceId).stream()
                    .filter(t -> t.getTextContent() != null && !t.getTextContent().isBlank())
                    .toList();
            SubtitleTrack track = withContent.stream()
                    .filter(t -> "de".equalsIgnoreCase(t.getLanguageCode()))
                    .findFirst()
                    .or(() -> withContent.stream().findFirst())
                    .orElseThrow(() -> new IllegalStateException(
                            "Keine Untertitel mit Inhalt für Resource " + resourceId));

            log.info("Starte Testimonial-Zusammenfassung: resourceId={}", resourceId);
            String summary;
            try {
                summary = scriptRunner.run(track.getTextContent(), PROMPT_FILE, config);
            } finally {
                scriptRunner.unloadModel(config);
            }

            jobService.markDone(job, toJson(Map.of("text", summary)));
            log.info("Testimonial-Zusammenfassung fertig: resourceId={} länge={}", resourceId, summary.length());

        } catch (Exception e) {
            log.error("TESTIMONIAL_SUMMARY-Job {} fehlgeschlagen: {}", job.getId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
