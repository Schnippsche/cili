package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.OllamaConfig;
import de.toengi.cili.dto.resource.AiSummaryDto;
import de.toengi.cili.model.entity.AiSummary;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.AiSummaryRepository;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaAnalysisServiceTest {

    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock AiSummaryRepository aiSummaryRepository;
    @Mock ProcessingJobRepository jobRepository;
    @Mock ProcessingJobService jobService;

    OllamaAnalysisService service;

    private final Executor syncExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        OllamaConfig config = new OllamaConfig();
        config.setModel("qwen2.5:7b");
        config.setUrl("http://localhost:11434");
        config.setPromptName("prompt.txt");
        config.setScriptName("analyze_worker.py");
        config.setTimeoutMinutes(1);

        CiliGlobalConfig global = new CiliGlobalConfig();
        global.setPythonPath("python3");
        global.setScriptsDir("/tmp");
        OllamaScriptRunner runner = new OllamaScriptRunner(global);

        service = new OllamaAnalysisService(
                subtitleTrackRepository, aiSummaryRepository,
                jobRepository, jobService, config, runner, syncExecutor);
    }

    private ProcessingJob makeJob(String languageCode, boolean force) {
        return ProcessingJob.builder()
                .id(99L)
                .resourceId(1L)
                .type(ProcessingJobType.OLLAMA_ANALYSIS)
                .status(ProcessingJobStatus.PENDING)
                .result("{\"languageCode\":\"" + languageCode + "\",\"force\":" + force + "}")
                .build();
    }

    @Test
    void getAllSummaries_returnsEmptyWhenNone() {
        when(aiSummaryRepository.findByResourceId(1L)).thenReturn(List.of());

        List<AiSummaryDto> result = service.getAllSummaries(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllSummaries_returnsMappedDtos() {
        AiSummary s = new AiSummary();
        s.setLanguageCode("de");
        s.setSummary("Zusammenfassung");
        s.setCreatedAt(Instant.now());
        when(aiSummaryRepository.findByResourceId(1L)).thenReturn(List.of(s));

        List<AiSummaryDto> result = service.getAllSummaries(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).languageCode()).isEqualTo("de");
        assertThat(result.get(0).summary()).isEqualTo("Zusammenfassung");
    }

    @Test
    void execute_skipsCacheHitWhenNotForced() {
        AiSummary cached = new AiSummary();
        cached.setLanguageCode("de");
        cached.setSummary("Cached result");
        cached.setCreatedAt(Instant.now());
        when(aiSummaryRepository.findByResourceIdAndLanguageCode(1L, "de")).thenReturn(Optional.of(cached));

        service.execute(makeJob("de", false));

        verify(jobService).markDone(any(), contains("skipped"));
        verifyNoInteractions(subtitleTrackRepository);
    }

    @Test
    void execute_failsWhenNoTrackForLanguage() {
        when(aiSummaryRepository.findByResourceIdAndLanguageCode(1L, "de")).thenReturn(Optional.empty());
        when(subtitleTrackRepository.findByResourceId(1L)).thenReturn(List.of());

        service.execute(makeJob("de", false));

        verify(jobService).markFailed(any(), contains("Keine Untertitel"));
    }

    @Test
    void execute_failsWhenTrackHasNoContent() {
        SubtitleTrack empty = SubtitleTrack.builder().id(1L).resourceId(1L)
                .languageCode("de").storedName("a").format(SubtitleFormat.VTT)
                .textContent(null).build();
        when(aiSummaryRepository.findByResourceIdAndLanguageCode(1L, "de")).thenReturn(Optional.empty());
        when(subtitleTrackRepository.findByResourceId(1L)).thenReturn(List.of(empty));

        service.execute(makeJob("de", false));

        verify(jobService).markFailed(any(), any());
    }

    @Test
    void enqueueAnalysis_returnsDuplicateJobIfAlreadyActive() {
        ProcessingJob active = makeJob("de", false);
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(
                eq(1L), eq(ProcessingJobType.OLLAMA_ANALYSIS), any()))
                .thenReturn(List.of(active));

        long jobId = service.enqueueAnalysis(1L, "de", false);

        assertThat(jobId).isEqualTo(99L);
        verify(jobService, never()).createJob(any(), any(), any(), any());
    }

    @Test
    void enqueueAnalysis_createsNewJobIfNoActiveDuplicate() {
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(any(), any(), any()))
                .thenReturn(List.of());
        ProcessingJob newJob = makeJob("de", false);
        when(jobService.createJob(any(), any(), any(), any())).thenReturn(newJob);

        long jobId = service.enqueueAnalysis(1L, "de", false);

        assertThat(jobId).isEqualTo(99L);
        verify(jobService).createJob(any(), any(), any(), any());
    }

    @Test
    void enqueueAnalysis_createsNewJobIfActiveDuplicateHasDifferentLanguage() {
        ProcessingJob otherLang = makeJob("en", false);
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(
                eq(1L), eq(ProcessingJobType.OLLAMA_ANALYSIS), any()))
                .thenReturn(List.of(otherLang));
        ProcessingJob newJob = makeJob("de", false);
        when(jobService.createJob(any(), any(), any(), any())).thenReturn(newJob);

        long jobId = service.enqueueAnalysis(1L, "de", false);

        assertThat(jobId).isEqualTo(99L);
        verify(jobService).createJob(any(), any(), any(), any());
    }

    @Test
    void execute_ignoresCacheWhenForced() {
        SubtitleTrack tr = SubtitleTrack.builder().id(2L).resourceId(1L)
                .languageCode("de").storedName("a").format(SubtitleFormat.VTT)
                .textContent("content").build();
        when(subtitleTrackRepository.findByResourceId(1L)).thenReturn(List.of(tr));

        service.execute(makeJob("de", true));

        verify(subtitleTrackRepository).findByResourceId(1L);
        verify(jobService).markFailed(any(), any());
        verify(aiSummaryRepository, never()).findByResourceIdAndLanguageCode(any(), any());
    }
}
