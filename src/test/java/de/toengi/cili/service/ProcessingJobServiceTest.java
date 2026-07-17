package de.toengi.cili.service;

import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcessingJobServiceTest {

    @Mock ProcessingJobRepository repo;
    ProcessingJobService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProcessingJobService(repo);
        ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    void createJob_persistsWithPendingStatus() {
        ProcessingJob saved = ProcessingJob.builder()
            .id(1L).resourceId(42L).type(ProcessingJobType.VIDEO_ANALYSIS)
            .status(ProcessingJobStatus.PENDING).build();
        when(repo.save(any())).thenReturn(saved);

        ProcessingJob result = service.createJob(42L, ProcessingJobType.VIDEO_ANALYSIS, null, null);

        assertThat(result.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(result.getType()).isEqualTo(ProcessingJobType.VIDEO_ANALYSIS);
        verify(repo).save(any(ProcessingJob.class));
    }

    @Test
    void markRunning_setsWorkerLockAndStartTime() {
        ProcessingJob job = ProcessingJob.builder().id(1L).attempts(0).build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markRunning(job, "worker-1");

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.RUNNING);
        assertThat(job.getWorkerLock()).isEqualTo("worker-1");
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getAttempts()).isEqualTo(1);
    }

    @Test
    void markDone_clearsLockAndSetsFinishTime() {
        ProcessingJob job = ProcessingJob.builder().id(1L).build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markDone(job, "{\"result\":\"ok\"}");

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.DONE);
        assertThat(job.getWorkerLock()).isNull();
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getResult()).isEqualTo("{\"result\":\"ok\"}");
    }

    @Test
    void markFailed_withRetryBudget_remainsPending() {
        ProcessingJob job = ProcessingJob.builder().id(1L).attempts(1).maxAttempts(3).build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markFailed(job, "timeout");

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(job.getWorkerLock()).isNull();
        assertThat(job.getErrorMessage()).isEqualTo("timeout");
    }

    @Test
    void markFailed_exhaustedRetries_setsFailed() {
        ProcessingJob job = ProcessingJob.builder().id(1L).attempts(3).maxAttempts(3).build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markFailed(job, "timeout");

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
    }

    @Test
    void cancelWhisperJobIfActive_cancelsRunningJob() {
        ProcessingJob job = ProcessingJob.builder()
            .id(9L).resourceId(5L).type(ProcessingJobType.WHISPER_TRANSCRIBE)
            .status(ProcessingJobStatus.RUNNING).attempts(1).maxAttempts(3).build();
        when(repo.findByResourceIdAndType(5L, ProcessingJobType.WHISPER_TRANSCRIBE))
            .thenReturn(Optional.of(job));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelWhisperJobIfActive(5L);

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.CANCELLED);
    }

    @Test
    void cancelWhisperJobIfActive_doneJobNotTouched() {
        ProcessingJob job = ProcessingJob.builder()
            .id(9L).resourceId(5L).type(ProcessingJobType.WHISPER_TRANSCRIBE)
            .status(ProcessingJobStatus.DONE).build();
        when(repo.findByResourceIdAndType(5L, ProcessingJobType.WHISPER_TRANSCRIBE))
            .thenReturn(Optional.of(job));

        service.cancelWhisperJobIfActive(5L);

        verify(repo, never()).save(any());
    }
}
