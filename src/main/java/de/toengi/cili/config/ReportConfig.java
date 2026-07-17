package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.report")
@Getter @Setter
public class ReportConfig {
    /** Maximale Anzahl Erfahrungsberichte pro PDF-Bericht. */
    private int maxResults = 50;
}
