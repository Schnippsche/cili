package de.toengi.cili.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.support.CronExpression;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    /**
     * Lädt die echte src/main/resources/application.yml (kein hand-gebautes Source-Objekt) über
     * Spring Boots Config-Data-Mechanismus und bindet cili.telegram gegen die reale
     * @ConfigurationProperties-Bean. So fällt ein YAML-Tippfehler (z.B. "scriptName:" statt
     * "script-name:") beim Binden auf, nicht erst zur Laufzeit im Scheduler.
     */
    @Test
    void applicationYml_telegramSources_bindWithNonBlankFieldsAndParsableCron() {
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TelegramPropertiesTestConfig.class)
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();

                TelegramImportConfig bound = ctx.getBean(TelegramImportConfig.class);
                Map<String, TelegramImportConfig.Source> byName = bound.getSources().stream()
                    .collect(Collectors.toMap(TelegramImportConfig.Source::getName, s -> s));

                assertThat(byName).containsKeys(
                    "telegram-menschen", "telegram-tiere", "telegram-webinare");

                byName.values().forEach(source -> {
                    assertThat(source.getName()).as("name").isNotBlank();
                    assertThat(source.getScriptName()).as("scriptName (%s)", source.getName()).isNotBlank();
                    assertThat(source.getEnvName()).as("envName (%s)", source.getName()).isNotBlank();
                    assertThat(source.getCron()).as("cron (%s)", source.getName()).isNotBlank();
                    assertThatCode(() -> CronExpression.parse(source.getCron()))
                        .as("cron parses for %s", source.getName())
                        .doesNotThrowAnyException();
                });
            });
    }

    @EnableConfigurationProperties(TelegramImportConfig.class)
    static class TelegramPropertiesTestConfig {
    }
}
