package de.toengi.cili.config;

import de.toengi.cili.service.MailflowStepProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailflowBatchScheduler {

    private final MailflowStepProcessor stepProcessor;

    /**
     * Fällt auf den Spring-Sentinelwert "-" zurück, wenn cili.mailflows.batch-cron nicht gesetzt
     * ist (z.B. weil noch keine Flows konfiguriert sind) — "-" bedeutet für
     * {@code @Scheduled(cron=...)} "nie ausführen", der Trigger wird also sauber deaktiviert statt
     * beim Start mit einem ungültigen Cron-Ausdruck zu crashen.
     */
    @Scheduled(cron = "${cili.mailflows.batch-cron:-}")
    public void run() {
        stepProcessor.processDueSteps();
    }
}
