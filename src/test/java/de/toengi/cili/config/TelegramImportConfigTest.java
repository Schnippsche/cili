package de.toengi.cili.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramImportConfigTest {

    private TelegramImportConfig config;

    @BeforeEach
    void setUp() {
        config = new TelegramImportConfig();
        TelegramImportConfig.Source lifestyle = new TelegramImportConfig.Source();
        lifestyle.setName("telegram-menschen");
        lifestyle.setLabel("Menschen");
        lifestyle.setScriptName("telegram_import_mensch.py");
        lifestyle.setEnvName("telegram_import_menschen.env");
        lifestyle.setCron("0 0 1 * * *");

        TelegramImportConfig.Source tiere = new TelegramImportConfig.Source();
        tiere.setName("telegram-tiere");
        tiere.setLabel("Tiere");
        tiere.setScriptName("telegram_import_tier.py");
        tiere.setEnvName("telegram_import_tiere.env");
        tiere.setCron("0 30 1 * * *");

        config.setSources(List.of(lifestyle, tiere));
    }

    @Test
    void findSource_knownName_returnsSource() {
        assertThat(config.findSource("telegram-tiere"))
            .isPresent()
            .get()
            .satisfies(s -> {
                assertThat(s.getEnvName()).isEqualTo("telegram_import_tiere.env");
                assertThat(s.getScriptName()).isEqualTo("telegram_import_tier.py");
            });
    }

    @Test
    void findSource_unknownName_returnsEmpty() {
        assertThat(config.findSource("does-not-exist")).isEmpty();
    }
}
