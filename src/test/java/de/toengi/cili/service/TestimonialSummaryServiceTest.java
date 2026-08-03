package de.toengi.cili.service;

import de.toengi.cili.config.OllamaConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestimonialSummaryServiceTest {

    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock ProcessingJobRepository jobRepository;
    @Mock ProcessingJobService jobService;
    @Mock OllamaScriptRunner scriptRunner;

    TestimonialSummaryService service;
    private final Executor syncExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        OllamaConfig config = new OllamaConfig();
        config.setModel("qwen2.5:7b");
        config.setUrl("http://localhost:11434");

        service = new TestimonialSummaryService(
                subtitleTrackRepository, jobRepository, jobService, config, scriptRunner, syncExecutor);
    }

    private ProcessingJob makeJob() {
        return ProcessingJob.builder()
                .id(42L)
                .resourceId(7L)
                .type(ProcessingJobType.TESTIMONIAL_SUMMARY)
                .status(ProcessingJobStatus.PENDING)
                .build();
    }

    @Test
    void enqueueSummary_returnsDuplicateJobIfAlreadyActive() {
        ProcessingJob active = makeJob();
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(
                eq(7L), eq(ProcessingJobType.TESTIMONIAL_SUMMARY), any()))
                .thenReturn(List.of(active));

        long jobId = service.enqueueSummary(7L);

        assertThat(jobId).isEqualTo(42L);
        verify(jobService, never()).createJob(any(), any(), any(), any());
    }

    @Test
    void enqueueSummary_createsNewJobIfNoActiveDuplicate() {
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(any(), any(), any()))
                .thenReturn(List.of());
        ProcessingJob newJob = makeJob();
        when(jobService.createJob(any(), any(), any(), any(), eq(1))).thenReturn(newJob);

        long jobId = service.enqueueSummary(7L);

        assertThat(jobId).isEqualTo(42L);
        // maxAttempts=1: einmalige, nutzerausgelöste Aktion soll bei Fehlschlag sofort FAILED werden
        // statt still auf PENDING zu bleiben (siehe ProcessingJobService.markFailed).
        verify(jobService).createJob(eq(7L), eq(ProcessingJobType.TESTIMONIAL_SUMMARY), any(), any(), eq(1));
    }

    @Test
    void enqueueSummary_createsNewJobIfExistingDuplicateAlreadyFailedOnce() {
        // PENDING + gesetzte errorMessage = ein Job, der bereits einmal fehlgeschlagen ist und nur auf
        // den nächsten automatischen Retry wartet (siehe ProcessingJobService.markFailed). Ein manueller
        // Retry-Klick muss trotzdem einen frischen Job anstoßen, statt die alte, stecken gebliebene
        // Job-ID zurückzugeben.
        ProcessingJob stuck = makeJob();
        stuck.setErrorMessage("boom");
        when(jobRepository.findByResourceIdAndTypeAndStatusIn(
                eq(7L), eq(ProcessingJobType.TESTIMONIAL_SUMMARY), any()))
                .thenReturn(List.of(stuck));
        ProcessingJob freshJob = ProcessingJob.builder()
                .id(43L).resourceId(7L)
                .type(ProcessingJobType.TESTIMONIAL_SUMMARY)
                .status(ProcessingJobStatus.PENDING)
                .build();
        when(jobService.createJob(any(), any(), any(), any(), eq(1))).thenReturn(freshJob);

        long jobId = service.enqueueSummary(7L);

        assertThat(jobId).isEqualTo(43L);
        verify(jobService).createJob(eq(7L), eq(ProcessingJobType.TESTIMONIAL_SUMMARY), any(), any(), eq(1));
    }

    @Test
    void execute_failsWhenNoSubtitleTrack() {
        when(subtitleTrackRepository.findByResourceId(7L)).thenReturn(List.of());

        service.execute(makeJob());

        verify(jobService).markFailed(any(), contains("Keine Untertitel"));
        verifyNoInteractions(scriptRunner);
    }

    @Test
    void execute_failsWhenNoTrackHasContent() {
        SubtitleTrack empty = SubtitleTrack.builder().id(1L).resourceId(7L)
                .languageCode("de").storedName("a").format(SubtitleFormat.VTT)
                .textContent(null).build();
        when(subtitleTrackRepository.findByResourceId(7L)).thenReturn(List.of(empty));

        service.execute(makeJob());

        verify(jobService).markFailed(any(), contains("Keine Untertitel"));
        verifyNoInteractions(scriptRunner);
    }

    @Test
    void execute_prefersGermanTrackWhenMultipleHaveContent() throws Exception {
        SubtitleTrack en = SubtitleTrack.builder().id(1L).resourceId(7L)
                .languageCode("en").storedName("a").format(SubtitleFormat.VTT)
                .textContent("English transcript").build();
        SubtitleTrack de = SubtitleTrack.builder().id(2L).resourceId(7L)
                .languageCode("de").storedName("b").format(SubtitleFormat.VTT)
                .textContent("Deutsches Transkript").build();
        when(subtitleTrackRepository.findByResourceId(7L)).thenReturn(List.of(en, de));
        when(scriptRunner.run(any(), any(), any())).thenReturn("Kurzer Bericht");

        service.execute(makeJob());

        verify(scriptRunner).run(eq("Deutsches Transkript"), eq("testimonial_summary_prompt.txt"), any());
    }

    @Test
    void execute_marksDoneWithGeneratedText() throws Exception {
        SubtitleTrack track = SubtitleTrack.builder().id(1L).resourceId(7L)
                .languageCode("de").storedName("a").format(SubtitleFormat.VTT)
                .textContent("Rohes Transkript").build();
        when(subtitleTrackRepository.findByResourceId(7L)).thenReturn(List.of(track));
        when(scriptRunner.run(eq("Rohes Transkript"), eq("testimonial_summary_prompt.txt"), any()))
                .thenReturn("Kurzer Bericht");

        service.execute(makeJob());

        verify(jobService).markDone(any(), contains("Kurzer Bericht"));
        verify(scriptRunner).unloadModel(any());
    }

    @Test
    void execute_marksFailedWhenScriptThrows() throws Exception {
        SubtitleTrack track = SubtitleTrack.builder().id(1L).resourceId(7L)
                .languageCode("de").storedName("a").format(SubtitleFormat.VTT)
                .textContent("Rohes Transkript").build();
        when(subtitleTrackRepository.findByResourceId(7L)).thenReturn(List.of(track));
        when(scriptRunner.run(any(), any(), any())).thenThrow(new IOException("boom"));

        service.execute(makeJob());

        verify(jobService).markFailed(any(), contains("boom"));
        verify(scriptRunner).unloadModel(any());
    }

    @Test
    void getActiveJobs_delegatesToRepository() {
        ProcessingJob job = makeJob();
        when(jobRepository.findActiveTestimonialSummaryJobs(eq(7L), any())).thenReturn(List.of(job));

        List<ProcessingJob> result = service.getActiveJobs(7L);

        assertThat(result).containsExactly(job);
    }
}
