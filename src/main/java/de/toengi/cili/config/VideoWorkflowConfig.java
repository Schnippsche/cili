package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.video-workflow")
@Getter @Setter
public class VideoWorkflowConfig {
    private int zombieTimeoutMinutes = 10;
    private int maxRetries = 3;
}
