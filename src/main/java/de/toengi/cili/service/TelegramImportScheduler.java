package de.toengi.cili.service;

import de.toengi.cili.config.TelegramImportConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramImportScheduler {

    private final TelegramImportConfig config;
    private final TelegramImportService telegramImportService;

    @Scheduled(cron = "${cili.telegram.cron:0 0 1 * * *}")
    public void runNightly() {
        if (!config.isEnabled()) {
            log.debug("Telegram-Import deaktiviert (cili.telegram.enabled=false) — übersprungen");
            return;
        }
        log.info("Geplanter Telegram-Import wird gestartet");
        try {
            telegramImportService.triggerAndRun();
        } catch (IllegalStateException e) {
            log.warn("Geplanter Telegram-Import übersprungen: {}", e.getMessage());
        }
    }
}
