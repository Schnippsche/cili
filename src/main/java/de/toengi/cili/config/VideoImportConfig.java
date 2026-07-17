package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cili.video-import")
@Getter @Setter
public class VideoImportConfig {

    private String scriptName = "video_upload.py";

    /** Basis-URL dieser CILI-Instanz — wird dem Python-Skript als CILI_URL übergeben */
    private String ciliUrl = "http://localhost:8080";
    private String ciliUser = "admin";
    private String ciliPass = "admin";

    private int maxHeight = 720;
    private int timeoutMinutes = 120;

    /** Pfad zur Netscape-Cookie-Datei (z.B. /var/cili/cookies/youtube_cookies.txt). */
    private String cookiesFile;
}
