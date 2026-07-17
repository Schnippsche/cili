package de.toengi.cili.controller.admin;

import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.enums.ProcessingJobStatus;
import de.toengi.cili.model.enums.ProcessingJobType;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.service.TelegramImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJobControllerTest {

    @Mock ProcessingJobRepository repo;
    @Mock TelegramImportService telegramImportService;
    @InjectMocks AdminJobController controller;

    @Test
    void triggerTelegramImport_success_returnsOk() {
        ProcessingJob job = ProcessingJob.builder().id(1L).type(ProcessingJobType.TELEGRAM_IMPORT)
            .source("telegram-tiere").status(ProcessingJobStatus.PENDING).build();
        when(telegramImportService.triggerAndRun("telegram-tiere")).thenReturn(job);

        ResponseEntity<?> response = controller.triggerTelegramImport("telegram-tiere");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void triggerTelegramImport_unknownSource_returns404() {
        when(telegramImportService.triggerAndRun("unknown"))
            .thenThrow(new IllegalArgumentException("Unbekannte Telegram-Quelle: unknown"));

        ResponseEntity<?> response = controller.triggerTelegramImport("unknown");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void triggerTelegramImport_alreadyRunning_returns409() {
        when(telegramImportService.triggerAndRun("telegram-tiere"))
            .thenThrow(new IllegalStateException("Ein Telegram-Import-Job für 'telegram-tiere' läuft bereits"));

        ResponseEntity<?> response = controller.triggerTelegramImport("telegram-tiere");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listSources_mapsConfigSourcesToDto() {
        var tiere = new de.toengi.cili.config.TelegramImportConfig.Source();
        tiere.setName("telegram-tiere");
        tiere.setLabel("Tiere");
        when(telegramImportService.listSources()).thenReturn(List.of(tiere));

        var result = controller.listSources();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("telegram-tiere");
        assertThat(result.get(0).label()).isEqualTo("Tiere");
    }
}
