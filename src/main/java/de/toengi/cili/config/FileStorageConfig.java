package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.storage")
@Getter @Setter
public class FileStorageConfig {
    private String basePath = "/var/cili/data";
    private String ffmpegPath = "/usr/bin/ffmpeg";
    private String ffprobePath = "/usr/bin/ffprobe";
    private String libreOfficePath = "/usr/bin/soffice";
}
