package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.libreoffice")
@Getter @Setter
public class LibreOfficeConfig {
    private int timeoutMinutes = 5;
}
