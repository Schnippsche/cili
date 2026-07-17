package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.nllb")
@Getter @Setter
public class NllbConfig {
    private String scriptName = "translate_worker.py";
    private String docScriptName = "doc_translate_worker.py";
    private String modelPath = "/opt/cili/nllb/nllb-600M";
    private String device = "cpu";
    private String computeType = "int8";
    private int beamSize = 4;
    private int batchSize = 32;
    private int timeoutMinutes = 10;
}
