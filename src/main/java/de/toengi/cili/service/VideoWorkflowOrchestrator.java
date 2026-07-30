package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.AudioConfig;
import de.toengi.cili.config.WhisperConfig;
import de.toengi.cili.dto.video.VideoAnalysisResult;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.SubtitleTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class VideoWorkflowOrchestrator implements JobDispatcher {

    public static final String WAV_PATH = "wavPath";

    private final ProcessingJobService jobService;
    private final VideoAnalysisService analysisService;
    private final VideoTranscodeService transcodeService;
    private final WavExtractService wavService;
    private final WhisperTranscriptionService whisperService;
    private final AudioNormalizeService audioNormalizeService;
    private final Executor transcodeExecutor;
    private final Executor gpuExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final WhisperConfig whisperConfig;
    private final AudioConfig audioConfig;
    private final SubtitleTranslationService translationService;
    private final OllamaAnalysisService ollamaAnalysisService;

    public VideoWorkflowOrchestrator(
            ProcessingJobService jobService,
            VideoAnalysisService analysisService,
            VideoTranscodeService transcodeService,
            WavExtractService wavService,
            WhisperTranscriptionService whisperService,
            AudioNormalizeService audioNormalizeService,
            @Qualifier("transcodeExecutor") Executor transcodeExecutor,
            @Qualifier("gpuExecutor") Executor gpuExecutor,
            SubtitleTrackRepository subtitleTrackRepository,
            WhisperConfig whisperConfig,
            AudioConfig audioConfig,
            SubtitleTranslationService translationService,
            OllamaAnalysisService ollamaAnalysisService) {
        this.jobService = jobService;
        this.analysisService = analysisService;
        this.transcodeService = transcodeService;
        this.wavService = wavService;
        this.whisperService = whisperService;
        this.audioNormalizeService = audioNormalizeService;
        this.transcodeExecutor = transcodeExecutor;
        this.gpuExecutor = gpuExecutor;
        this.subtitleTrackRepository = subtitleTrackRepository;
        this.whisperConfig = whisperConfig;
        this.audioConfig = audioConfig;
        this.translationService = translationService;
        this.ollamaAnalysisService = ollamaAnalysisService;
    }

    public void enqueueVideoWorkflow(Long resourceId) {
        ProcessingJob job = jobService.createJob(resourceId, ProcessingJobType.VIDEO_ANALYSIS, null,
                toJson(Map.of("skipWhisper", false)));
        dispatch(job);
    }

    public void enqueueVideoWorkflowWithoutWhisper(Long resourceId) {
        ProcessingJob job = jobService.createJob(resourceId, ProcessingJobType.VIDEO_ANALYSIS, null,
                toJson(Map.of("skipWhisper", true)));
        dispatch(job);
    }

    public void enqueueRetranscribe(Long resourceId) {
        ProcessingJob job = jobService.createJob(resourceId, ProcessingJobType.WAV_EXTRACT, null,
                toJson(Map.of("forceWhisper", true)));
        dispatch(job);
    }

    public void startAudioWorkflow(Long resourceId) {
        if (subtitleTrackRepository.existsByResourceIdAndLanguageCode(resourceId, whisperConfig.getLanguage())) {
            log.info("Untertitel bereits vorhanden für resource {} (lang={}) — Audio-Workflow übersprungen",
                    resourceId, whisperConfig.getLanguage());
            return;
        }
        ProcessingJob job = audioConfig.isNormalize()
                ? jobService.createJob(resourceId, ProcessingJobType.AUDIO_NORMALIZE, null, null)
                : jobService.createJob(resourceId, ProcessingJobType.WAV_EXTRACT, null, null);
        dispatch(job);
    }

    @Override
    public void dispatch(ProcessingJob job) {
        switch (job.getType()) {
            case VIDEO_ANALYSIS  -> transcodeExecutor.execute(() -> runVideoAnalysis(job));
            case VIDEO_TRANSCODE -> transcodeExecutor.execute(() -> runVideoTranscode(job));
            case WAV_EXTRACT     -> transcodeExecutor.execute(() -> runWavExtract(job));
            case AUDIO_NORMALIZE -> transcodeExecutor.execute(() -> runAudioNormalize(job));
            case WHISPER_TRANSCRIBE -> gpuExecutor.execute(() -> whisperService.execute(job));
            case SUBTITLE_TRANSLATE -> gpuExecutor.execute(() -> translationService.execute(job));
            case OLLAMA_ANALYSIS    -> gpuExecutor.execute(() -> ollamaAnalysisService.execute(job));
            default -> log.warn("dispatch() aufgerufen für nicht behandelten Job-Typ {}", job.getType());
        }
    }

    private void runVideoAnalysis(ProcessingJob job) {
        // skipWhisper muss vor markDone gelesen werden, da markDone job.result überschreibt
        boolean skipWhisper = readBoolParam(job, "skipWhisper");
        try {
            jobService.markRunning(job, "analysis:" + Thread.currentThread().getName());
            VideoAnalysisResult analysis = analysisService.analyze(job.getResourceId(), job);
            jobService.markDone(job, toJson(analysis));

            if (analysis.requiresProcessing()) {
                ProcessingJob next = jobService.createJob(job.getResourceId(),
                        ProcessingJobType.VIDEO_TRANSCODE, job.getId(),
                        toJson(Map.of("skipWhisper", skipWhisper)));
                dispatch(next);
            } else if (!skipWhisper) {
                ProcessingJob wavJob = jobService.createJob(job.getResourceId(),
                        ProcessingJobType.WAV_EXTRACT, job.getId(), null);
                dispatch(wavJob);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jobService.markFailed(job, "Interrupted");
        } catch (Exception e) {
            log.error("VIDEO_ANALYSIS fehlgeschlagen für resource {}: {}", job.getResourceId(), e.getMessage(), e);
            jobService.markFailed(job, e.getMessage());
        }
    }

    private void runVideoTranscode(ProcessingJob job) {
        // skipWhisper vor execute lesen, da execute job.result per markDone überschreibt
        boolean skipWhisper = readBoolParam(job, "skipWhisper");
        transcodeService.execute(job);
        if (!skipWhisper) {
            ProcessingJob wavJob = jobService.createJob(job.getResourceId(),
                    ProcessingJobType.WAV_EXTRACT, job.getId(), null);
            dispatch(wavJob);
        }
    }

    private void runWavExtract(ProcessingJob job) {
        try {
            boolean forceWhisper = readBoolParam(job, "forceWhisper");
            Path wavPath = wavService.execute(job);
            if (wavPath == null) {
                log.info("Keine Audiospur für resource {} — Whisper-Transkription übersprungen", job.getResourceId());
                return;
            }
            if (!forceWhisper && subtitleTrackRepository.existsByResourceIdAndLanguageCode(
                    job.getResourceId(), whisperConfig.getLanguage())) {
                log.info("Untertitel bereits vorhanden für resource {} (lang={}) — Whisper übersprungen, WAV gelöscht",
                        job.getResourceId(), whisperConfig.getLanguage());
                try { Files.deleteIfExists(wavPath); } catch (IOException ignored) {}
            } else {
                String init = toJson(Map.of(WAV_PATH, wavPath.toString()));
                ProcessingJob whisperJob = jobService.createJob(job.getResourceId(),
                        ProcessingJobType.WHISPER_TRANSCRIBE, job.getId(), init);
                dispatch(whisperJob);
            }
        } catch (Exception e) {
            log.error("WAV_EXTRACT Verkettung fehlgeschlagen für resource {}", job.getResourceId(), e);
        }
    }

    private void runAudioNormalize(ProcessingJob job) {
        audioNormalizeService.execute(job);
        ProcessingJob wavJob = jobService.createJob(job.getResourceId(),
                ProcessingJobType.WAV_EXTRACT, job.getId(), null);
        dispatch(wavJob);
    }

    private boolean readBoolParam(ProcessingJob job, String key) {
        try {
            String result = job.getResult();
            if (result == null || result.isBlank()) return false;
            return objectMapper.readTree(result).path(key).asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
