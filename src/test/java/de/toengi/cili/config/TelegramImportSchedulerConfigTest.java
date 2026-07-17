package de.toengi.cili.config;

import de.toengi.cili.service.TelegramImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TelegramImportSchedulerConfigTest {

    @Mock TelegramImportConfig config;
    @Mock TelegramImportService service;
    @Mock ScheduledTaskRegistrar registrar;

    TelegramImportSchedulerConfig scheduler;

    private TelegramImportConfig.Source source(String name, String cron) {
        TelegramImportConfig.Source s = new TelegramImportConfig.Source();
        s.setName(name);
        s.setLabel(name);
        s.setEnvName(name + ".env");
        s.setCron(cron);
        return s;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        scheduler = new TelegramImportSchedulerConfig(config, service);
    }

    @Test
    void configureTasks_enabledWithSources_registersOneTriggerTaskPerSource() {
        when(config.isEnabled()).thenReturn(true);
        when(config.getSources()).thenReturn(List.of(
            source("telegram-lifestyle", "0 0 1 * * *"),
            source("telegram-tiere", "0 30 1 * * *")));

        scheduler.configureTasks(registrar);

        verify(registrar, times(2)).addTriggerTask(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    void configureTasks_disabled_registersNoTriggerTasks() {
        when(config.isEnabled()).thenReturn(false);

        scheduler.configureTasks(registrar);

        verify(registrar, never()).addTriggerTask(any(), any());
        verifyNoInteractions(service);
    }

    @Test
    void configureTasks_enabledButNoSources_registersNoTriggerTasks() {
        when(config.isEnabled()).thenReturn(true);
        when(config.getSources()).thenReturn(List.of());

        scheduler.configureTasks(registrar);

        verify(registrar, never()).addTriggerTask(any(), any());
    }

    @Test
    void configureTasks_missingCron_throwsIllegalArgumentNamingSource() {
        when(config.isEnabled()).thenReturn(true);
        when(config.getSources()).thenReturn(List.of(source("telegram-lifestyle", " ")));

        assertThatThrownBy(() -> scheduler.configureTasks(registrar))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("telegram-lifestyle");
    }

    @Test
    void runSource_alreadyRunning_isSwallowedAndLogged() {
        doThrow(new IllegalStateException("bereits aktiv")).when(service).triggerAndRun("telegram-lifestyle");

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(scheduler, "runSource", "telegram-lifestyle"))
            .doesNotThrowAnyException();

        verify(service).triggerAndRun("telegram-lifestyle");
    }

    @Test
    void runSource_unknownSource_isSwallowedAndLogged() {
        doThrow(new IllegalArgumentException("unbekannte Quelle")).when(service).triggerAndRun(anyString());

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(scheduler, "runSource", "does-not-exist"))
            .doesNotThrowAnyException();

        verify(service).triggerAndRun("does-not-exist");
    }
}
