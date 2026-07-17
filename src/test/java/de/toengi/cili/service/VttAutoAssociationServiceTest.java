package de.toengi.cili.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VttAutoAssociationServiceTest {

    @Test
    void parseVttFileName_withLanguage() {
        var result = VttAutoAssociationService.VttFileInfo.parse("Video1.de.vtt");
        assertThat(result).isNotNull();
        assertThat(result.baseName()).isEqualTo("Video1");
        assertThat(result.language()).isEqualTo("de");
    }

    @Test
    void parseVttFileName_withoutLanguage() {
        var result = VttAutoAssociationService.VttFileInfo.parse("Video1.vtt");
        assertThat(result).isNotNull();
        assertThat(result.baseName()).isEqualTo("Video1");
        assertThat(result.language()).isNull();
    }

    @Test
    void parseVttFileName_notAVtt() {
        var result = VttAutoAssociationService.VttFileInfo.parse("Video1.mp4");
        assertThat(result).isNull();
    }

    @Test
    void parseVideoFileName_getsBaseName() {
        assertThat(VttAutoAssociationService.videoBaseName("Video1.mp4")).isEqualTo("Video1");
        assertThat(VttAutoAssociationService.videoBaseName("My Film.mkv")).isEqualTo("My Film");
    }
}
