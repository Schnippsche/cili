package de.toengi.cili.service;

import de.toengi.cili.config.OllamaIdleConfig;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaIdleSchedulerTest {

    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock OllamaAnalysisService ollamaAnalysisService;
    @Mock ProcessingJobRepository jobRepository;

    OllamaIdleConfig config;
    OllamaIdleScheduler scheduler;

    @BeforeEach
    void setUp() {
        config = new OllamaIdleConfig();
        config.setEnabled(true);
        config.setLanguage("de");
        scheduler = new OllamaIdleScheduler(config, subtitleTrackRepository, ollamaAnalysisService, jobRepository);
    }

    @Test
    void skipsWhenDisabled() {
        config.setEnabled(false);
        scheduler.backfillIfIdle();
        verifyNoInteractions(subtitleTrackRepository, ollamaAnalysisService, jobRepository);
    }

    @Test
    void skipsWhenActiveGpuJob() {
        when(jobRepository.existsActiveJobOfTypes(any())).thenReturn(true);
        scheduler.backfillIfIdle();
        verifyNoInteractions(subtitleTrackRepository);
        verifyNoInteractions(ollamaAnalysisService);
    }

    @Test
    void skipsWhenNoResourceWithMissingSummary() {
        when(jobRepository.existsActiveJobOfTypes(any())).thenReturn(false);
        when(subtitleTrackRepository.findOldestResourceMissingSummary("de"))
                .thenReturn(Optional.empty());
        scheduler.backfillIfIdle();
        verifyNoInteractions(ollamaAnalysisService);
    }

    @Test
    void enqueuesOneJobWhenIdleAndResourceFound() {
        when(jobRepository.existsActiveJobOfTypes(any())).thenReturn(false);
        when(subtitleTrackRepository.findOldestResourceMissingSummary("de"))
                .thenReturn(Optional.of(42L));
        scheduler.backfillIfIdle();
        verify(ollamaAnalysisService).enqueueAsSystem(42L, "de");
        verify(subtitleTrackRepository, times(1)).findOldestResourceMissingSummary("de");
    }
}
