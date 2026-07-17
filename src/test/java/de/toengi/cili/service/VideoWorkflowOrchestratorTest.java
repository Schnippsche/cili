package de.toengi.cili.service;

import de.toengi.cili.config.AudioConfig;
import de.toengi.cili.config.WhisperConfig;
import de.toengi.cili.dto.video.VideoAnalysisResult;
import de.toengi.cili.dto.video.VideoAnalysisResult.TranscodeAction;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.SubtitleTrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoWorkflowOrchestratorTest {

    @Mock ProcessingJobService jobService;
    @Mock VideoAnalysisService analysisService;
    @Mock VideoTranscodeService transcodeService;
    @Mock WavExtractService wavService;
    @Mock WhisperTranscriptionService whisperService;
    @Mock AudioNormalizeService audioNormalizeService;
    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock SubtitleTranslationService translationService;
    @Mock OllamaAnalysisService ollamaAnalysisService;

    WhisperConfig whisperConfig;
    AudioConfig audioConfig;
    VideoWorkflowOrchestrator orchestrator;

    /** Synchroner Executor — führt Tasks direkt im aufrufenden Thread aus, ideal für Tests. */
    private static final Executor SYNC = Runnable::run;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        whisperConfig = new WhisperConfig();
        whisperConfig.setLanguage("de");
        audioConfig = new AudioConfig();
        audioConfig.setNormalize(false);
        orchestrator = new VideoWorkflowOrchestrator(
                jobService, analysisService, transcodeService,
                wavService, whisperService, audioNormalizeService,
                SYNC, SYNC,
                subtitleTrackRepository, whisperConfig, audioConfig,
                translationService, ollamaAnalysisService);
    }

    @Test
    void dispatch_videoAnalysis_transcodeNeeded_chainsToTranscodeThenWavThenWhisper() throws Exception {
        ProcessingJob analysisJob = job(1L, 10L, ProcessingJobType.VIDEO_ANALYSIS, "{\"skipWhisper\":false}");
        ProcessingJob transcodeJob = job(2L, 10L, ProcessingJobType.VIDEO_TRANSCODE, "{\"skipWhisper\":false}");
        ProcessingJob wavJob      = job(3L, 10L, ProcessingJobType.WAV_EXTRACT, null);
        ProcessingJob whisperJob  = job(4L, 10L, ProcessingJobType.WHISPER_TRANSCRIBE, null);

        when(analysisService.analyze(10L, analysisJob))
                .thenReturn(new VideoAnalysisResult(1920, 1080, "hevc", "aac", 8_000_000L, 60.0, TranscodeAction.TRANSCODE));
        when(jobService.createJob(eq(10L), eq(ProcessingJobType.VIDEO_TRANSCODE), eq(1L), anyString()))
                .thenReturn(transcodeJob);
        when(jobService.createJob(eq(10L), eq(ProcessingJobType.WAV_EXTRACT), eq(2L), isNull()))
                .thenReturn(wavJob);
        when(wavService.execute(wavJob)).thenReturn(Path.of("/tmp/audio.wav"));
        when(jobService.createJob(eq(10L), eq(ProcessingJobType.WHISPER_TRANSCRIBE), eq(3L), anyString()))
                .thenReturn(whisperJob);
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(anyLong(), anyString())).thenReturn(false);

        orchestrator.dispatch(analysisJob);

        verify(analysisService).analyze(10L, analysisJob);
        verify(transcodeService).execute(transcodeJob);
        verify(wavService).execute(wavJob);
        verify(whisperService).execute(whisperJob);
    }

    @Test
    void dispatch_videoAnalysis_noTranscodeNeeded_skipsTranscodeRunsWav() throws Exception {
        ProcessingJob analysisJob = job(1L, 10L, ProcessingJobType.VIDEO_ANALYSIS, "{\"skipWhisper\":false}");
        ProcessingJob wavJob     = job(3L, 10L, ProcessingJobType.WAV_EXTRACT, null);
        ProcessingJob whisperJob = job(4L, 10L, ProcessingJobType.WHISPER_TRANSCRIBE, null);

        when(analysisService.analyze(10L, analysisJob))
                .thenReturn(new VideoAnalysisResult(1280, 720, "h264", "aac", 2_000_000L, 60.0, TranscodeAction.SKIP));
        when(jobService.createJob(eq(10L), eq(ProcessingJobType.WAV_EXTRACT), eq(1L), isNull()))
                .thenReturn(wavJob);
        when(wavService.execute(wavJob)).thenReturn(Path.of("/tmp/audio.wav"));
        when(jobService.createJob(eq(10L), eq(ProcessingJobType.WHISPER_TRANSCRIBE), eq(3L), anyString()))
                .thenReturn(whisperJob);
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(anyLong(), anyString())).thenReturn(false);

        orchestrator.dispatch(analysisJob);

        verify(transcodeService, never()).execute(any());
        verify(wavService).execute(wavJob);
        verify(whisperService).execute(whisperJob);
    }

    @Test
    void dispatch_videoAnalysis_skipWhisper_transcodeButNoWav() throws Exception {
        ProcessingJob analysisJob = job(1L, 10L, ProcessingJobType.VIDEO_ANALYSIS, "{\"skipWhisper\":true}");
        ProcessingJob transcodeJob = job(2L, 10L, ProcessingJobType.VIDEO_TRANSCODE, "{\"skipWhisper\":true}");

        when(analysisService.analyze(10L, analysisJob))
                .thenReturn(new VideoAnalysisResult(1920, 1080, "hevc", "aac", 8_000_000L, 60.0, TranscodeAction.TRANSCODE));
        when(jobService.createJob(eq(10L), eq(ProcessingJobType.VIDEO_TRANSCODE), eq(1L), anyString()))
                .thenReturn(transcodeJob);

        orchestrator.dispatch(analysisJob);

        verify(transcodeService).execute(transcodeJob);
        verify(wavService, never()).execute(any());
        verify(whisperService, never()).execute(any());
    }

    @Test
    void dispatch_videoAnalysis_analysisThrows_workflowAbortsGracefully() throws Exception {
        ProcessingJob analysisJob = job(1L, 10L, ProcessingJobType.VIDEO_ANALYSIS, "{\"skipWhisper\":false}");
        when(analysisService.analyze(10L, analysisJob)).thenThrow(new RuntimeException("ffprobe not found"));

        orchestrator.dispatch(analysisJob);

        verify(wavService, never()).execute(any());
        verify(whisperService, never()).execute(any());
    }

    @Test
    void dispatch_wavExtract_subtitleAlreadyExists_skipsWhisper() throws Exception {
        ProcessingJob wavJob = job(3L, 10L, ProcessingJobType.WAV_EXTRACT, null);
        when(wavService.execute(wavJob)).thenReturn(Path.of("/tmp/audio.wav"));
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(10L, "de")).thenReturn(true);

        orchestrator.dispatch(wavJob);

        verify(whisperService, never()).execute(any());
    }

    @Test
    void dispatch_wavExtract_forceWhisper_runsWhisperEvenIfSubtitleExists() throws Exception {
        ProcessingJob wavJob     = job(3L, 10L, ProcessingJobType.WAV_EXTRACT, "{\"forceWhisper\":true}");
        ProcessingJob whisperJob = job(4L, 10L, ProcessingJobType.WHISPER_TRANSCRIBE, null);
        when(wavService.execute(wavJob)).thenReturn(Path.of("/tmp/audio.wav"));
        when(subtitleTrackRepository.existsByResourceIdAndLanguageCode(10L, "de")).thenReturn(true);
        when(jobService.createJob(eq(10L), eq(ProcessingJobType.WHISPER_TRANSCRIBE), eq(3L), anyString()))
                .thenReturn(whisperJob);

        orchestrator.dispatch(wavJob);

        verify(whisperService).execute(whisperJob);
    }

    @Test
    void dispatch_subtitleTranslate_callsTranslationService() {
        ProcessingJob job = job(5L, 10L, ProcessingJobType.SUBTITLE_TRANSLATE, null);

        orchestrator.dispatch(job);

        verify(translationService).execute(job);
        verify(whisperService, never()).execute(any());
    }

    private static ProcessingJob job(long id, long resourceId, ProcessingJobType type, String result) {
        return ProcessingJob.builder().id(id).resourceId(resourceId).type(type).result(result).build();
    }
}
