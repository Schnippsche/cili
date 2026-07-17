package de.toengi.cili.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NllbConfigTest {

    @Test
    void defaultValues_areSetCorrectly() {
        NllbConfig cfg = new NllbConfig();
        assertThat(cfg.getScriptName()).isEqualTo("translate_worker.py");
        assertThat(cfg.getDocScriptName()).isEqualTo("doc_translate_worker.py");
        assertThat(cfg.getModelPath()).isEqualTo("/opt/cili/nllb/nllb-600M");
        assertThat(cfg.getDevice()).isEqualTo("cpu");
        assertThat(cfg.getComputeType()).isEqualTo("int8");
        assertThat(cfg.getBeamSize()).isEqualTo(4);
        assertThat(cfg.getBatchSize()).isEqualTo(32);
        assertThat(cfg.getTimeoutMinutes()).isEqualTo(10);
    }
}
