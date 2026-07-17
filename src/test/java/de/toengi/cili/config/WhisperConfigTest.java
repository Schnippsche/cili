package de.toengi.cili.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WhisperConfigTest {

    @Test
    void defaults_areSet() {
        WhisperConfig cfg = new WhisperConfig();
        assertThat(cfg.getScriptName()).isEqualTo("transcribe_worker.py");
        assertThat(cfg.getLanguage()).isEqualTo("auto");
        assertThat(cfg.getModel()).isEqualTo("large-v3");
        assertThat(cfg.getDevice()).isEqualTo("cuda");
        assertThat(cfg.getComputeType()).isEqualTo("int8_float16");
        assertThat(cfg.getTimeoutMinutes()).isEqualTo(30);
        assertThat(cfg.getGlossaryPath()).isNull();
        assertThat(cfg.getMaxChars()).isEqualTo(84);
        assertThat(cfg.getMaxDuration()).isEqualTo(6.0);
        assertThat(cfg.getCuePad()).isEqualTo(0.2);
        assertThat(cfg.getNoSpeechThreshold()).isEqualTo(0.6);
    }
}
