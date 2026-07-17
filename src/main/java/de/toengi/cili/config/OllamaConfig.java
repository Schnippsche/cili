package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.ollama")
@Getter @Setter
public class OllamaConfig {
    private String url = "http://localhost:11434";
    private String model = "qwen2.5:7b";
    private String promptName;
    private String scriptName = "analyze_worker.py";
    private int timeoutMinutes = 5;
    private int numCtx = 32768;
}
