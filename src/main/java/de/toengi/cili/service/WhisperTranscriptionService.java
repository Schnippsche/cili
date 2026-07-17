package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.WhisperConfig;
import de.toengi.cili.util.PythonProcessUtils;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class WhisperTranscriptionService {

    private final ProcessingJobService jobService;
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final WhisperConfig config;
    private final CiliGlobalConfig global;
    private final LanguageOptionService languageOptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhisperTranscriptionService(ProcessingJobService jobService,
                                       SubtitleTrackRepository subtitleTrackRepository,
                                       ResourceRepository resourceRepository,
                                       StorageService storageService,
                                       WhisperConfig config,
                                       CiliGlobalConfig global,
                                       LanguageOptionService languageOptionService) {
        this.jobService = jobService;
        this.subtitleTrackRepository = subtitleTrackRepository;
        this.resourceRepository = resourceRepository;
        this.storageService = storageService;
        this.config = config;
        this.global = global;
        this.languageOptionService = languageOptionService;
    }

    public void execute(ProcessingJob job) {
        if (isCancelled(job)) {
            log.info("Job {} cancelled before transcription", job.getId());
            deleteWavQuietly(job);
            return;
        }
        jobService.markRunning(job, "whisper:" + Thread.currentThread().getName());
        try {
            Path wavPath = extractWavPath(job);
            doTranscribe(job, wavPath);
        } catch (Exception e) {
            log.error("Whisper transcription failed for job {}: {}", job.getId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
        }
    }

    private void deleteWavQuietly(ProcessingJob job) {
        try {
            Files.deleteIfExists(extractWavPath(job));
        } catch (Exception e) {
            log.warn("Could not delete WAV for cancelled job {}: {}", job.getId(), e.getMessage());
        }
    }

    private boolean isAutoLanguage() {
        return "auto".equalsIgnoreCase(config.getLanguage());
    }

    private String resolveDetectedLanguage(Path langOutputPath) {
        if (langOutputPath == null || !langOutputPath.toFile().exists()) {
            return config.getLanguage();
        }
        try {
            return objectMapper.readTree(Files.readString(langOutputPath))
                    .path("language").asText(config.getLanguage());
        } catch (IOException e) {
            log.warn("Could not read detected language from {}: {}", langOutputPath, e.getMessage());
            return config.getLanguage();
        }
    }

    private void doTranscribe(ProcessingJob job, Path wavPath) throws Exception {
        Path vttPath = Files.createTempFile("whisper-", ".vtt");
        Path langOutputPath = Files.createTempFile("whisper-lang-", ".json");
        try {
            Optional<Path> syncRef = resolveSyncRef(job.getResourceId());
            List<String> cmd = buildCommand(wavPath, vttPath, syncRef, langOutputPath);
            log.info("Starting faster-whisper for resource {}: {}", job.getResourceId(), cmd);

            ProcessBuilder pb = PythonProcessUtils.forScript(cmd, global.resolve(config.getScriptName()));
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            pipeToLog(proc);

            // Polling: alle 10s auf Abbruch prüfen
            while (!proc.waitFor(10, TimeUnit.SECONDS)) {
                if (isCancelled(job)) {
                    proc.destroyForcibly();
                    log.info("Job {} cancelled — subprocess killed", job.getId());
                    return;
                }
            }

            int rc = proc.exitValue();
            if (rc != 0) {
                throw new IOException("transcribe_worker.py exited with code " + rc);
            }

            if (isCancelled(job)) {
                log.info("Job {} cancelled after transcription, discarding result", job.getId());
                return;
            }

            String vttContent = Files.readString(vttPath);
            String detectedLanguage = resolveDetectedLanguage(langOutputPath);
            log.info("Detected language for resource {}: {}", job.getResourceId(), detectedLanguage);
            storeSubtitle(job.getResourceId(), vttContent, detectedLanguage);

            jobService.markDone(job, objectMapper.writeValueAsString(
                Map.of("language", detectedLanguage)));

        } finally {
            Files.deleteIfExists(wavPath);
            Files.deleteIfExists(vttPath);
            Files.deleteIfExists(langOutputPath);
        }
    }

    List<String> buildCommand(Path wavPath, Path vttPath, Optional<Path> syncRef, Path langOutputPath) {
        List<String> cmd = new ArrayList<>(List.of(
            global.getPythonPath(),
            global.resolve(config.getScriptName()),
            "--wav",          wavPath.toString(),
            "--output",       vttPath.toString(),
            "--model",        config.getModel(),
            "--device",       config.getDevice(),
            "--compute-type", config.getComputeType(),
            "--beam-size",    String.valueOf(config.getBeamSize()),
            "--cpu-threads",  String.valueOf(config.getCpuThreads()),
            "--max-chars",    String.valueOf(config.getMaxChars()),
            "--max-duration", String.valueOf(config.getMaxDuration()),
            "--cue-pad",      String.valueOf(config.getCuePad()),
            "--sync",                  config.getSync(),
            "--no-speech-threshold",   String.valueOf(config.getNoSpeechThreshold())
        ));
        if (!isAutoLanguage()) {
            cmd.add("--lang");
            cmd.add(config.getLanguage());
        }
        if (langOutputPath != null) {
            cmd.add("--lang-output");
            cmd.add(langOutputPath.toString());
        }
        if (config.getGlossaryPath() != null && !config.getGlossaryPath().isBlank()) {
            cmd.add("--glossary");
            cmd.add(config.getGlossaryPath());
        }
        if (config.getCorrectionsPath() != null && !config.getCorrectionsPath().isBlank()) {
            cmd.add("--corrections");
            cmd.add(config.getCorrectionsPath());
        }
        // Sync-Referenz: Originalmedium der Resource. Bei Videoquellen liefert es
        // die Video-Dauer für die Drift-Korrektur; bei reinen Audioquellen findet
        // das Skript keinen Video-Stream und überspringt die Korrektur (No-Op).
        if (!"off".equalsIgnoreCase(config.getSync())) {
            syncRef.ifPresent(ref -> {
                cmd.add("--sync-ref");
                cmd.add(ref.toString());
            });
        }
        return cmd;
    }

    /** Löst den Originalmedienpfad der Resource für --sync-ref auf (best effort). */
    private Optional<Path> resolveSyncRef(Long resourceId) {
        if ("off".equalsIgnoreCase(config.getSync())) {
            return Optional.empty();
        }
        try {
            return resourceRepository.findById(resourceId)
                .map(Resource::getStoredName)
                .flatMap(storageService::resolveLocalPath);
        } catch (Exception e) {
            log.warn("Could not resolve sync-ref for resource {}: {}", resourceId, e.getMessage());
            return Optional.empty();
        }
    }

    private void pipeToLog(Process proc) {
        Thread out = new Thread(() -> {
            try (var r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(line -> log.info("[whisper] {}", line));
            } catch (IOException ignored) {}
        });
        Thread err = new Thread(() -> {
            try (var r = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(line -> log.warn("[whisper-err] {}", line));
            } catch (IOException ignored) {}
        });
        out.setDaemon(true);
        err.setDaemon(true);
        out.start();
        err.start();
    }

    private boolean isCancelled(ProcessingJob job) {
        try {
            ProcessingJob fresh = jobService.reloadJob(job.getId());
            return fresh.getStatus() == ProcessingJobStatus.CANCELLED;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private void storeSubtitle(Long resourceId, String vttContent, String language) {
        subtitleTrackRepository.findByResourceIdAndLanguageCode(resourceId, language)
            .ifPresent(subtitleTrackRepository::delete);

        subtitleTrackRepository.save(SubtitleTrack.builder()
            .resourceId(resourceId)
            .languageCode(language)
            .label("Auto")
            .storedName(UUID.randomUUID().toString())
            .format(SubtitleFormat.VTT)
            .textContent(vttContent)
            .build());
        log.info("Stored VTT for resource {} (lang={})", resourceId, language);
    }

    private Path extractWavPath(ProcessingJob job) throws IOException {
        if (job.getResult() == null) {
            throw new IllegalStateException("Job has no result: " + job.getId());
        }
        String p = objectMapper.readTree(job.getResult()).path("wavPath").asText(null);
        if (p == null) {
            throw new IllegalStateException("wavPath missing in job result: " + job.getId());
        }
        return Path.of(p);
    }
}
