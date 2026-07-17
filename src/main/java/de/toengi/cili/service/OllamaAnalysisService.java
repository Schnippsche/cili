package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.OllamaConfig;
import de.toengi.cili.util.PythonProcessUtils;
import de.toengi.cili.dto.resource.AiSummaryDto;
import de.toengi.cili.model.entity.AiSummary;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.AiSummaryRepository;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OllamaAnalysisService {

    private final SubtitleTrackRepository subtitleTrackRepository;
    private final AiSummaryRepository aiSummaryRepository;
    private final ProcessingJobRepository jobRepository;
    private final ProcessingJobService jobService;
    private final OllamaConfig config;
    private final CiliGlobalConfig global;
    private final Executor gpuExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public OllamaAnalysisService(
            SubtitleTrackRepository subtitleTrackRepository,
            AiSummaryRepository aiSummaryRepository,
            ProcessingJobRepository jobRepository,
            ProcessingJobService jobService,
            OllamaConfig config,
            CiliGlobalConfig global,
            @Qualifier("gpuExecutor") Executor gpuExecutor) {
        this.subtitleTrackRepository = subtitleTrackRepository;
        this.aiSummaryRepository = aiSummaryRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.config = config;
        this.global = global;
        this.gpuExecutor = gpuExecutor;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public List<AiSummaryDto> getAllSummaries(Long resourceId) {
        return aiSummaryRepository.findByResourceId(resourceId).stream()
                .map(s -> new AiSummaryDto(s.getLanguageCode(), s.getSummary(), s.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public List<ProcessingJob> getActiveJobs(Long resourceId) {
        return jobRepository.findActiveAnalysisJobs(resourceId, java.time.LocalDateTime.now().minusHours(2));
    }

    private static final List<ProcessingJobStatus> ACTIVE = List.of(
            ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING);

    /** Stellt einen Analyse-Job in die Warteschlange und gibt die Job-ID zurück. */
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public long enqueueAnalysis(Long resourceId, String languageCode, boolean force) {
        Optional<ProcessingJob> dup = findActiveDuplicate(resourceId, languageCode);
        if (dup.isPresent()) {
            log.info("OLLAMA_ANALYSIS für resource {} (lang={}) bereits aktiv — Job {} zurückgegeben",
                    resourceId, languageCode, dup.get().getId());
            return dup.get().getId();
        }
        String params = toJson(Map.of("languageCode", languageCode, "force", force));
        ProcessingJob job = jobService.createJob(resourceId, ProcessingJobType.OLLAMA_ANALYSIS, null, params);
        gpuExecutor.execute(() -> execute(job));
        return job.getId();
    }

    /** Wird vom Idle-Scheduler aufgerufen — kein Benutzerkontext vorhanden. */
    public long enqueueAsSystem(Long resourceId, String languageCode) {
        Optional<ProcessingJob> dup = findActiveDuplicate(resourceId, languageCode);
        if (dup.isPresent()) {
            log.info("OLLAMA_ANALYSIS für resource {} (lang={}) bereits aktiv — Job {} zurückgegeben (System)",
                    resourceId, languageCode, dup.get().getId());
            return dup.get().getId();
        }
        String params = toJson(Map.of("languageCode", languageCode, "force", false));
        ProcessingJob job = jobService.createJob(resourceId, ProcessingJobType.OLLAMA_ANALYSIS, null, params);
        gpuExecutor.execute(() -> execute(job));
        return job.getId();
    }

    private Optional<ProcessingJob> findActiveDuplicate(Long resourceId, String languageCode) {
        return jobRepository.findByResourceIdAndTypeAndStatusIn(
                        resourceId, ProcessingJobType.OLLAMA_ANALYSIS, ACTIVE)
                .stream()
                .filter(j -> languageCode.equals(parseParam(j, "languageCode")))
                .findFirst();
    }

    private String parseParam(ProcessingJob job, String key) {
        try {
            if (job.getResult() == null) return null;
            return objectMapper.readTree(job.getResult()).path(key).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Wird vom Executor und vom JobRecoveryService aufgerufen. */
    public void execute(ProcessingJob job) {
        jobService.markRunning(job, "ollama:" + Thread.currentThread().getName());
        try {
            String languageCode = readParam(job, "languageCode");
            boolean force = Boolean.parseBoolean(readParam(job, "force"));
            Long resourceId = job.getResourceId();

            if (!force) {
                if (aiSummaryRepository.findByResourceIdAndLanguageCode(resourceId, languageCode).isPresent()) {
                    log.info("Ollama-Job {}: Zusammenfassung bereits vorhanden, übersprungen", job.getId());
                    jobService.markDone(job, toJson(Map.of("skipped", true, "languageCode", languageCode)));
                    return;
                }
            }

            SubtitleTrack track = subtitleTrackRepository.findByResourceId(resourceId).stream()
                    .filter(t -> languageCode.equalsIgnoreCase(t.getLanguageCode())
                            && t.getTextContent() != null && !t.getTextContent().isBlank())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Keine Untertitel mit Inhalt für Sprache: " + languageCode));

            log.info("Starte Ollama-Analyse: resourceId={} lang={}", resourceId, languageCode);
            String summary;
            try {
                summary = runScript(track.getTextContent());
            } finally {
                unloadModel();
            }

            AiSummary entity = aiSummaryRepository
                    .findByResourceIdAndLanguageCode(resourceId, languageCode)
                    .orElseGet(AiSummary::new);
            entity.setResourceId(resourceId);
            entity.setLanguageCode(languageCode);
            entity.setSummary(summary);
            entity.setCreatedAt(Instant.now());
            aiSummaryRepository.save(entity);
            log.info("Ollama-Analyse gespeichert: resourceId={} lang={} länge={}", resourceId, languageCode, summary.length());
            jobService.markDone(job, toJson(Map.of("languageCode", languageCode)));

        } catch (Exception e) {
            log.error("Ollama-Job {} fehlgeschlagen: {}", job.getId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
        }
    }

    private String runScript(String subtitleContent) throws IOException, InterruptedException {
        Path textFile = Files.createTempFile("ollama-text-", ".txt");
        try {
            Files.writeString(textFile, subtitleContent, StandardCharsets.UTF_8);

            List<String> cmd = List.of(
                    global.getPythonPath(),
                    global.resolve(config.getScriptName()),
                    "--text-file",   textFile.toString(),
                    "--prompt-file", global.resolve(config.getPromptName()),
                    "--model",       config.getModel(),
                    "--url",         config.getUrl(),
                    "--timeout",     String.valueOf(config.getTimeoutMinutes() * 60),
                    "--num-ctx",     String.valueOf(config.getNumCtx())
            );

            log.info("Starte analyze_worker: {}", cmd);
            ProcessBuilder pb = PythonProcessUtils.forScript(cmd, global.resolve(config.getScriptName()));
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            Thread errThread = new Thread(() -> {
                try (var r = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    r.lines().forEach(line -> log.info("[analyze] {}", line));
                } catch (IOException ignored) {}
            });
            errThread.setDaemon(true);
            errThread.start();

            byte[][] stdoutHolder = {null};
            Thread stdoutThread = new Thread(() -> {
                try { stdoutHolder[0] = proc.getInputStream().readAllBytes(); }
                catch (IOException ignored) {}
            });
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            boolean finished = proc.waitFor(config.getTimeoutMinutes(), TimeUnit.MINUTES);
            if (!finished) {
                proc.destroyForcibly();
                throw new IOException("analyze_worker.py Timeout nach " + config.getTimeoutMinutes() + " Minuten");
            }
            if (proc.exitValue() != 0) {
                throw new IOException("analyze_worker.py Exit-Code " + proc.exitValue());
            }
            stdoutThread.join(config.getTimeoutMinutes() * 60_000L);
            return stdoutHolder[0] != null ? new String(stdoutHolder[0], StandardCharsets.UTF_8).trim() : "";

        } finally {
            Files.deleteIfExists(textFile);
        }
    }

    private void unloadModel() {
        try {
            String body = toJson(Map.of("model", config.getModel(), "keep_alive", 0));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl() + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            log.info("Ollama-Modell '{}' aus VRAM entladen (keep_alive=0 gesendet)", config.getModel());
            waitForVramReleased();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Konnte Ollama-Modell nicht entladen: {}", e.getMessage());
        }
    }

    private void waitForVramReleased() throws InterruptedException {
        HttpRequest psReq = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl() + "/api/ps"))
                .GET()
                .timeout(java.time.Duration.ofSeconds(5))
                .build();
        for (int i = 0; i < 15; i++) {
            Thread.sleep(2_000);
            try {
                HttpResponse<String> resp = httpClient.send(psReq, HttpResponse.BodyHandlers.ofString());
                if (!resp.body().contains(config.getModel())) {
                    log.info("Ollama-Modell '{}' nach {}s aus VRAM entfernt", config.getModel(), (i + 1) * 2);
                    return;
                }
            } catch (Exception e) {
                log.debug("Ollama /api/ps nicht erreichbar: {}", e.getMessage());
                return;
            }
        }
        log.warn("Ollama-Modell '{}' nach 30s noch im VRAM — nächster Job startet trotzdem", config.getModel());
    }

    private String readParam(ProcessingJob job, String key) {
        try {
            return objectMapper.readTree(job.getResult()).path(key).asText();
        } catch (Exception e) {
            throw new IllegalStateException("Konnte Job-Parameter '" + key + "' nicht lesen: " + job.getResult(), e);
        }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
