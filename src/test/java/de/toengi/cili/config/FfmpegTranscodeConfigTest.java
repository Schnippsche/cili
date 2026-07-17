package de.toengi.cili.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(FfmpegTranscodeConfig.class)
@TestPropertySource(properties = {
    "cili.ffmpeg.target-height=720",
    "cili.ffmpeg.video-codec=libx264",
    "cili.ffmpeg.audio-codec=aac",
    "cili.ffmpeg.crf=23",
    "cili.ffmpeg.preset=medium",
    "cili.ffmpeg.max-bitrate=4000k",
    "cili.ffmpeg.parallel-jobs=2",
    "cili.ffmpeg.timeout-minutes=60",
    "cili.ffmpeg.temp-dir=/tmp/cili-ffmpeg"
})
class FfmpegTranscodeConfigTest {

    @Autowired
    FfmpegTranscodeConfig config;

    @Test
    void bindsAllProperties() {
        assertThat(config.getTargetHeight()).isEqualTo(720);
        assertThat(config.getVideoCodec()).isEqualTo("libx264");
        assertThat(config.getAudioCodec()).isEqualTo("aac");
        assertThat(config.getCrf()).isEqualTo(23);
        assertThat(config.getPreset()).isEqualTo("medium");
        assertThat(config.getMaxBitrate()).isEqualTo("4000k");
        assertThat(config.getParallelJobs()).isEqualTo(2);
        assertThat(config.getTimeoutMinutes()).isEqualTo(60);
        assertThat(config.getTempDir()).isEqualTo("/tmp/cili-ffmpeg");
    }
}
