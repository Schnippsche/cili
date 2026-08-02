package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.TelegramImportConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramImportServiceTest {

    @Mock TelegramImportConfig config;
    @Mock CiliGlobalConfig global;
    @Mock ProcessingJobService jobService;
    @Mock ProcessingJobRepository jobRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserDetailsService userDetailsService;
    @Mock TelegramImportService self;

    TelegramImportService service;

    private TelegramImportConfig.Source tiereSource() {
        TelegramImportConfig.Source s = new TelegramImportConfig.Source();
        s.setName("telegram-tiere");
        s.setLabel("Tiere");
        s.setScriptName("telegram_import_tier.py");
        s.setEnvName("telegram_import_tiere.env");
        s.setCron("0 30 1 * * *");
        return s;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TelegramImportService(config, global, jobService, jobRepository, jwtTokenProvider, userDetailsService);
        ReflectionTestUtils.setField(service, "self", self);
    }

    @Test
    void isRunning_delegatesToScopedRepositoryQuery() {
        when(jobRepository.existsByTypeAndSourceAndStatusIn(
            ProcessingJobType.TELEGRAM_IMPORT, "telegram-tiere",
            List.of(ProcessingJobStatus.PENDING, ProcessingJobStatus.RUNNING)))
            .thenReturn(true);

        assertThat(service.isRunning("telegram-tiere")).isTrue();
    }

    @Test
    void triggerAndRun_unknownSource_throwsIllegalArgument() {
        when(config.findSource("unknown")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.triggerAndRun("unknown"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jobService);
    }

    @Test
    void triggerAndRun_alreadyRunningForSource_throwsIllegalState() {
        when(config.findSource("telegram-tiere")).thenReturn(java.util.Optional.of(tiereSource()));
        when(jobRepository.existsByTypeAndSourceAndStatusIn(
            eq(ProcessingJobType.TELEGRAM_IMPORT), eq("telegram-tiere"), anyList()))
            .thenReturn(true);

        assertThatThrownBy(() -> service.triggerAndRun("telegram-tiere"))
            .isInstanceOf(IllegalStateException.class);

        verify(jobService, never()).createSystemJob(any(), anyString(), any());
    }

    @Test
    void triggerAndRun_success_createsJobWithSourceAndDispatchesAsync() {
        when(config.findSource("telegram-tiere")).thenReturn(java.util.Optional.of(tiereSource()));
        when(jobRepository.existsByTypeAndSourceAndStatusIn(
            eq(ProcessingJobType.TELEGRAM_IMPORT), eq("telegram-tiere"), anyList()))
            .thenReturn(false);
        ProcessingJob job = ProcessingJob.builder().id(42L).type(ProcessingJobType.TELEGRAM_IMPORT)
            .source("telegram-tiere").status(ProcessingJobStatus.PENDING).build();
        when(jobService.createSystemJob(ProcessingJobType.TELEGRAM_IMPORT, "telegram-tiere", null))
            .thenReturn(job);

        ProcessingJob result = service.triggerAndRun("telegram-tiere");

        assertThat(result.getId()).isEqualTo(42L);
        verify(self).executeAsync(eq(42L), any(TelegramImportConfig.Source.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildCommand_usesResolvedEnvPathOfGivenSource() {
        when(global.getPythonPath()).thenReturn("python3");
        when(global.resolve("telegram_import_tier.py")).thenReturn("/opt/cili/scripts/telegram_import_tier.py");
        when(global.resolve("telegram_import_tiere.env")).thenReturn("/opt/cili/scripts/telegram_import_tiere.env");

        List<String> cmd = (List<String>) ReflectionTestUtils.invokeMethod(
            service, "buildCommand", tiereSource());

        assertThat(cmd).containsExactly(
            "python3", "/opt/cili/scripts/telegram_import_tier.py",
            "--env", "/opt/cili/scripts/telegram_import_tiere.env");
    }
}
