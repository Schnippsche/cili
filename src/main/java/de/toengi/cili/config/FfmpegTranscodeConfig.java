package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.ffmpeg")
@Getter @Setter
public class FfmpegTranscodeConfig {
    private int targetHeight = 720;
    private String videoCodec = "libx264";
    private String audioCodec = "aac";
    private int crf = 23;
    private String preset = "medium";
    private String maxBitrate = "4000k";
    private int parallelJobs = 2;
    private int timeoutMinutes = 60;
    private String tempDir = "/tmp/cili-ffmpeg";
}
