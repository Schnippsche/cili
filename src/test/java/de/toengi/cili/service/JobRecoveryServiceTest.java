package de.toengi.cili.service;

import de.toengi.cili.config.VideoWorkflowConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobRecoveryServiceTest {

    @Mock ProcessingJobRepository repo;
    @Mock JobDispatcher dispatcher;
    @Mock ProcessingJobService jobService;
    VideoWorkflowConfig config;
    JobRecoveryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new VideoWorkflowConfig();
        config.setZombieTimeoutMinutes(10);
        service = new JobRecoveryService(repo, dispatcher, jobService, config);
    }

    @Test
    void recoverZombies_bulkResetsAndRedispatches() {
        ProcessingJob zombie = ProcessingJob.builder()
            .id(5L).resourceId(10L).type(ProcessingJobType.VIDEO_TRANSCODE)
            .status(ProcessingJobStatus.RUNNING)
            .updatedAt(LocalDateTime.now().minusMinutes(15))
            .build();
        when(repo.findZombieJobs(any())).thenReturn(List.of(zombie));

        service.recoverZombieJobs();

        verify(repo).resetJobsToPending(List.of(5L));
        verify(dispatcher).dispatch(zombie);
    }

    @Test
    void recoverZombies_noZombies_doesNothing() {
        when(repo.findZombieJobs(any())).thenReturn(List.of());

        service.recoverZombieJobs();

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void recoverOnStartup_pendingJobsAreDispatched() {
        ProcessingJob pending = ProcessingJob.builder()
            .id(7L).resourceId(20L).type(ProcessingJobType.VIDEO_ANALYSIS)
            .status(ProcessingJobStatus.PENDING)
            .build();
        when(repo.findByStatusIn(anyList())).thenReturn(List.of(pending));

        service.recoverOnStartup();

        verify(repo).resetJobsToPending(List.of(7L));
        verify(dispatcher).dispatch(pending);
    }

    @Test
    void recoverZombies_externalProcessJobs_areMarkedFailedNotReDispatched() {
        ProcessingJob telegramJob = ProcessingJob.builder()
            .id(10L).type(ProcessingJobType.TELEGRAM_IMPORT)
            .status(ProcessingJobStatus.RUNNING)
            .updatedAt(LocalDateTime.now().minusMinutes(15))
            .build();
        when(repo.findZombieJobs(any())).thenReturn(List.of(telegramJob));

        service.recoverZombieJobs();

        verify(jobService).markFailed(eq(telegramJob), any());
        verify(dispatcher, never()).dispatch(any());
        verify(repo, never()).resetJobsToPending(anyList());
    }

    @Test
    void recoverOnStartup_externalProcessJobs_areMarkedFailedNotReDispatched() {
        ProcessingJob videoImportJob = ProcessingJob.builder()
            .id(11L).type(ProcessingJobType.VIDEO_URL_IMPORT)
            .status(ProcessingJobStatus.RUNNING)
            .build();
        when(repo.findByStatusIn(anyList())).thenReturn(List.of(videoImportJob));

        service.recoverOnStartup();

        verify(jobService).markFailed(eq(videoImportJob), any());
        verify(dispatcher, never()).dispatch(any());
        verify(repo, never()).resetJobsToPending(anyList());
    }

    @Test
    void recoverOnStartup_videoClipJobs_areMarkedFailedNotReDispatched() {
        ProcessingJob clipJob = ProcessingJob.builder()
            .id(12L).type(ProcessingJobType.VIDEO_CLIP)
            .status(ProcessingJobStatus.PENDING)
            .build();
        when(repo.findByStatusIn(anyList())).thenReturn(List.of(clipJob));

        service.recoverOnStartup();

        verify(jobService).markFailed(eq(clipJob), any());
        verify(dispatcher, never()).dispatch(any());
        verify(repo, never()).resetJobsToPending(anyList());
    }
}
