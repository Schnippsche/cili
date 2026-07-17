package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.ollama.idle-backfill")
@Getter @Setter
public class OllamaIdleConfig {
    private boolean enabled = false;
    private String language = "de";
}
