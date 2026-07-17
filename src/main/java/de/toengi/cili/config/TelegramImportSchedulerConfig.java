package de.toengi.cili.config;

import de.toengi.cili.service.TelegramImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TelegramImportSchedulerConfig implements SchedulingConfigurer {

    private final TelegramImportConfig config;
    private final TelegramImportService service;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!config.isEnabled()) {
            log.debug("Telegram-Import deaktiviert (cili.telegram.enabled=false) — kein Scheduling");
            return;
        }
        for (TelegramImportConfig.Source source : config.getSources()) {
            String name = source.getName();
            registrar.addTriggerTask(
                () -> runSource(name),
                ctx -> new CronTrigger(source.getCron()).nextExecution(ctx));
        }
    }

    private void runSource(String name) {
        log.info("Geplanter Telegram-Import ({}) wird gestartet", name);
        try {
            service.triggerAndRun(name);
        } catch (IllegalStateException e) {
            log.warn("Geplanter Telegram-Import ({}) übersprungen: {}", name, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Geplanter Telegram-Import ({}): Quelle nicht in Config gefunden: {}", name, e.getMessage());
        }
    }
}
