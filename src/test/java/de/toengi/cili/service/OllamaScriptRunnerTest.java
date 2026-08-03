package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.OllamaConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaScriptRunnerTest {

    @Test
    void run_throwsWhenScriptMissing() {
        CiliGlobalConfig global = new CiliGlobalConfig();
        global.setPythonPath("python3");
        global.setScriptsDir("/tmp/does-not-exist-cili-test");
        OllamaScriptRunner runner = new OllamaScriptRunner(global);

        OllamaConfig config = new OllamaConfig();
        config.setScriptName("analyze_worker.py");
        config.setTimeoutMinutes(1);

        assertThatThrownBy(() -> runner.run("text", "prompt.txt", config))
                .isInstanceOf(IOException.class);
    }

    @Test
    void unloadModel_swallowsConnectionErrors() {
        CiliGlobalConfig global = new CiliGlobalConfig();
        OllamaScriptRunner runner = new OllamaScriptRunner(global);

        OllamaConfig config = new OllamaConfig();
        config.setUrl("http://localhost:1"); // niemand lauscht dort

        assertThatCode(() -> runner.unloadModel(config)).doesNotThrowAnyException();
    }
}
