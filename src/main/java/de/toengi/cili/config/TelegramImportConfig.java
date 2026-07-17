package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cili.telegram")
@Getter @Setter
public class TelegramImportConfig {

    private boolean enabled = false;

    private String scriptName = "telegram_import.py";

    /** Dateiname der .env-Datei im scriptsDir; null → Skript sucht selbst nach .env im Arbeitsverzeichnis */
    private String envName;

    /** Cron-Ausdruck für den nächtlichen Lauf (Standard: 01:00 Uhr täglich) */
    private String cron = "0 0 1 * * *";

    /** Timeout in Minuten; danach wird der Python-Prozess abgebrochen */
    private int timeoutMinutes = 60;
}
