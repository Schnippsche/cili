package de.toengi.cili.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(LibreOfficeConfig.class)
@TestPropertySource(properties = {
    "cili.libreoffice.timeout-minutes=7"
})
class LibreOfficeConfigTest {

    @Autowired
    LibreOfficeConfig config;

    @Test
    void bindsTimeoutMinutes() {
        assertThat(config.getTimeoutMinutes()).isEqualTo(7);
    }
}
