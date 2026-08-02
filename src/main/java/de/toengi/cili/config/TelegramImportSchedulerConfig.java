package de.toengi.cili.config;

import de.toengi.cili.service.TelegramImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.util.Assert;

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
        if (config.getSources().isEmpty()) {
            log.warn("Telegram-Import aktiviert (cili.telegram.enabled=true), aber keine Quellen konfiguriert "
                + "(cili.telegram.sources ist leer) — es wird kein Scheduling registriert. "
                + "Vermutlich fehlerhafte YAML-Konfiguration.");
            return;
        }
        for (TelegramImportConfig.Source source : config.getSources()) {
            String name = source.getName();
            Assert.hasText(source.getCron(),
                () -> "cili.telegram.sources: cron fehlt für Quelle '" + name + "'");
            Assert.hasText(source.getScriptName(),
                () -> "cili.telegram.sources: script-name fehlt für Quelle '" + name + "'");
            registrar.addTriggerTask(() -> runSource(name), new CronTrigger(source.getCron()));
        }
        log.info("Telegram-Import: {} Quelle(n) für Scheduling registriert", config.getSources().size());
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
