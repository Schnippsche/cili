package de.toengi.cili.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.NllbConfig;
import de.toengi.cili.util.PythonProcessUtils;
import de.toengi.cili.dto.job.ProcessingJobDto;
import de.toengi.cili.dto.translation.SubtitleTranslationRequest;
import de.toengi.cili.exception.CiliException;
import de.toengi.cili.exception.ConflictException;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SubtitleTranslationService {

    public static final String TARGET_LANG = "targetLang";
    private final ProcessingJobService jobService;
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final NllbConfig nllbConfig;
    private final CiliGlobalConfig global;
    private final Executor gpuExecutor;
    private final LanguageOptionService languageOptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubtitleTranslationService(
            ProcessingJobService jobService,
            SubtitleTrackRepository subtitleTrackRepository,
            ProcessingJobRepository processingJobRepository,
            NllbConfig nllbConfig,
            CiliGlobalConfig global,
            @Qualifier("gpuExecutor") Executor gpuExecutor,
            LanguageOptionService languageOptionService) {
        this.jobService = jobService;
        this.subtitleTrackRepository = subtitleTrackRepository;
        this.processingJobRepository = processingJobRepository;
        this.nllbConfig = nllbConfig;
        this.global = global;
        this.gpuExecutor = gpuExecutor;
        this.languageOptionService = languageOptionService;
    }

    private static final List<ProcessingJobStatus> ACTIVE = List.of(
            ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING);

    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'TRANSLATE_SUBTITLES')")
    public Map<String, Long> enqueueTranslation(Long resourceId, SubtitleTranslationRequest req,
                                                boolean overwrite) {
        // 0. Deduplication: aktiven Job für selbe Zielsprache zurückgeben
        Optional<ProcessingJob> dup = processingJobRepository
                .findByResourceIdAndTypeAndStatusIn(resourceId, ProcessingJobType.SUBTITLE_TRANSLATE, ACTIVE)
                .stream()
                .filter(j -> req.targetLang().equals(parseParam(j, TARGET_LANG)))
                .findFirst();
        if (dup.isPresent()) {
            log.info("SUBTITLE_TRANSLATE für resource {} → {} bereits aktiv — Job {} zurückgegeben",
                    resourceId, req.targetLang(), dup.get().getId());
            return Map.of("jobId", dup.get().getId());
        }

        // 1. Verify source track exists and belongs to this resource
        subtitleTrackRepository.findById(req.sourceTrackId())
            .filter(t -> t.getResourceId().equals(resourceId))
            .orElseThrow(() -> new ResourceNotFoundException(
                "SubtitleTrack", req.sourceTrackId()));

        // 2. Validate target language
        if (!languageOptionService.getSupportedTranslationCodes().contains(req.targetLang())) {
            throw new CiliException(
                "Unsupported language: " + req.targetLang(), HttpStatus.BAD_REQUEST);
        }

        // 3. Conflict check
        Optional<SubtitleTrack> existing =
            subtitleTrackRepository.findByResourceIdAndLanguageCode(resourceId, req.targetLang());
        if (existing.isPresent()) {
            if (!overwrite) {
                throw new ConflictException(
                    "Track for language '" + req.targetLang() + "' already exists");
            }
            subtitleTrackRepository.delete(existing.get());
            log.info("Deleted existing {} track for resource {} (overwrite=true)",
                req.targetLang(), resourceId);
        }

        // 4. Create job; result carries sourceTrackId + targetLang for the service
        String initResult;
        try {
            initResult = objectMapper.writeValueAsString(
                Map.of("sourceTrackId", req.sourceTrackId(), TARGET_LANG, req.targetLang()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize job params", e);
        }
        ProcessingJob job = jobService.createJob(
            resourceId, ProcessingJobType.SUBTITLE_TRANSLATE, null, initResult);

        gpuExecutor.execute(() -> execute(job));

        log.info("Enqueued SUBTITLE_TRANSLATE job {} for resource {} (→ {})",
            job.getId(), resourceId, req.targetLang());
        return Map.of("jobId", job.getId());
    }

    private String parseParam(ProcessingJob job, String key) {
        try {
            if (job.getResult() == null) return null;
            return objectMapper.readTree(job.getResult()).path(key).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#resourceId, 'RESOURCE', 'READ')")
    public List<ProcessingJobDto> getActiveJobs(Long resourceId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return processingJobRepository
            .findByResourceIdAndTypeAndCreatedAtAfter(
                resourceId, ProcessingJobType.SUBTITLE_TRANSLATE, since)
            .stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(ProcessingJobDto::from)
            .toList();
    }

    public void execute(ProcessingJob job) {
        try {
            if (isCancelled(job)) {
                log.info("Job {} cancelled before translation", job.getId());
                return;
            }
            jobService.markRunning(job, "translation:" + Thread.currentThread().getName());
            try {
                doTranslate(job);
            } catch (Exception e) {
                log.error("Translation failed for job {}: {}", job.getId(), e.getMessage(), e);
                jobService.markFailed(job, e.getMessage());
            }
        } catch (org.springframework.dao.OptimisticLockingFailureException | IllegalStateException e) {
            log.debug("Job {} no longer exists, translation aborted", job.getId());
        }
    }

    private void doTranslate(ProcessingJob job) throws Exception {
        JsonNode json = objectMapper.readTree(job.getResult());
        long sourceTrackId = json.path("sourceTrackId").asLong();
        String targetLang  = json.path(TARGET_LANG).asText();

        SubtitleTrack source = subtitleTrackRepository.findById(sourceTrackId)
            .orElseThrow(() -> new IllegalStateException("Source track not found: " + sourceTrackId));

        String vttContent = toVtt(source);

        Path inputVtt  = Files.createTempFile("cili-translate-in-",  ".vtt");
        Path outputVtt = Files.createTempFile("cili-translate-out-", ".vtt");

        try {
            Files.writeString(inputVtt, vttContent);

            List<String> cmd = buildCommand(inputVtt, outputVtt, source.getLanguageCode(), targetLang);
            log.info("Starting NLLB translation for job {} (resource {}): {}",
                job.getId(), job.getResourceId(), cmd);

            ProcessBuilder pb = PythonProcessUtils.forScript(cmd, global.resolve(nllbConfig.getScriptName()));
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            pipeToLog(proc, job.getId());

            // Poll for cancellation every 10 s; enforce overall timeout
            long deadline = System.currentTimeMillis()
                + nllbConfig.getTimeoutMinutes() * 60_000L;
            while (!proc.waitFor(10, TimeUnit.SECONDS)) {
                if (isCancelled(job)) {
                    proc.destroyForcibly();
                    log.info("Job {} cancelled — subprocess killed", job.getId());
                    return;
                }
                if (System.currentTimeMillis() > deadline) {
                    proc.destroyForcibly();
                    throw new IOException("translate_worker.py exceeded timeout of "
                        + nllbConfig.getTimeoutMinutes() + " min (job " + job.getId() + ")");
                }
            }

            int rc = proc.exitValue();
            if (rc != 0) {
                throw new IOException("translate_worker.py exited with code " + rc);
            }

            if (isCancelled(job)) {
                log.info("Job {} cancelled after translation, discarding result", job.getId());
                return;
            }

            String translated = Files.readString(outputVtt);
            storeTranslation(job.getResourceId(), translated, targetLang);

            jobService.markDone(job,
                objectMapper.writeValueAsString(Map.of(TARGET_LANG, targetLang)));

        } finally {
            Files.deleteIfExists(inputVtt);
            Files.deleteIfExists(outputVtt);
        }
    }

    /** Package-private for unit testing. */
    List<String> buildCommand(Path input, Path output, String sourceLang, String targetLang) {
        return new ArrayList<>(List.of(
            global.getPythonPath(),    global.resolve(nllbConfig.getScriptName()),
            "--input",                 input.toString(),
            "--output",                output.toString(),
            "--source",                sourceLang,
            "--target",                targetLang,
            "--model",                 nllbConfig.getModelPath(),
            "--device",                nllbConfig.getDevice(),
            "--compute-type",          nllbConfig.getComputeType(),
            "--beam-size",             String.valueOf(nllbConfig.getBeamSize()),
            "--batch-size",            String.valueOf(nllbConfig.getBatchSize())
        ));
    }

    /** Package-private for unit testing. Converts SRT content to VTT if needed. */
    String toVtt(SubtitleTrack track) {
        if (track.getFormat() == SubtitleFormat.VTT) {
            return track.getTextContent();
        }
        // SRT → VTT: add WEBVTT header, replace comma in timestamps with period
        return "WEBVTT\n\n"
            + track.getTextContent()
                .replaceAll("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})", "$1.$2");
    }

    private void storeTranslation(Long resourceId, String vttContent, String targetLang) {
        // Remove existing track for this language if present (overwrite was already cleared
        // in enqueueTranslation, but guard against race conditions)
        subtitleTrackRepository.findByResourceIdAndLanguageCode(resourceId, targetLang)
            .ifPresent(subtitleTrackRepository::delete);

        subtitleTrackRepository.save(SubtitleTrack.builder()
            .resourceId(resourceId)
            .languageCode(targetLang)
            .label(languageOptionService.getLabelForCode(targetLang) + " (" + targetLang + ")")
            .storedName(UUID.randomUUID().toString())
            .format(SubtitleFormat.VTT)
            .textContent(vttContent)
            .build());

        log.info("Stored NLLB translation for resource {} (lang={})", resourceId, targetLang);
    }

    private void pipeToLog(Process proc, Long jobId) {
        Thread out = new Thread(() -> {
            try (var r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(line -> log.info("[nllb-{}] {}", jobId, line));
            } catch (IOException ignored) {}
        });
        Thread err = new Thread(() -> {
            try (var r = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(line -> log.warn("[nllb-err-{}] {}", jobId, line));
            } catch (IOException ignored) {}
        });
        out.setDaemon(true);
        err.setDaemon(true);
        out.start();
        err.start();
    }

    private boolean isCancelled(ProcessingJob job) {
        return jobService.reloadJob(job.getId()).getStatus() == ProcessingJobStatus.CANCELLED;
    }
}
