package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Configuration
@ConfigurationProperties(prefix = "cili.telegram")
@Getter @Setter
public class TelegramImportConfig {

    private boolean enabled = false;

    private String scriptName = "telegram_import.py";

    /** Timeout in Minuten; danach wird der Python-Prozess abgebrochen */
    private int timeoutMinutes = 60;

    /** CILI-Benutzer, für den das Job-Token (CILI_TOKEN) generiert wird — muss in allen Quellen-.env-Dateien als CILI_USER hinterlegt sein. */
    private String ciliUser = "admin";

    /** Konfigurierte Telegram-Quellen (eine pro Gruppe/.env-Datei). */
    private List<Source> sources = new ArrayList<>();

    public Optional<Source> findSource(String name) {
        return sources.stream().filter(s -> name.equals(s.getName())).findFirst();
    }

    @Getter @Setter
    public static class Source {
        /** Stabiler Bezeichner — identisch zu TG_SOURCE im Skript und ProcessingJob.source/Testimonial.source */
        private String name;

        /** Anzeige-Label fürs Admin-Frontend */
        private String label;

        /** Dateiname der .env-Datei im scriptsDir */
        private String envName;

        /** Cron-Ausdruck für den nächtlichen Lauf dieser Quelle */
        private String cron;
    }
}
