package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.audio")
@Getter @Setter
public class AudioConfig {

    /** Audio-Normalisierung vor Transkription aktivieren */
    private boolean normalize = true;

    /**
     * ffmpeg -af Filter-Chain.
     * acompressor: reduziert Clipping/Übersteuerung
     * loudnorm:    EBU R128 Lautstärke-Normalisierung
     */
    private String filterChain =
        "acompressor=threshold=-20dB:ratio=4:attack=5:release=50," +
        "loudnorm=I=-16:TP=-1.5:LRA=11";

    /** Ausgabe-Bitrate für das normalisierte MP3 */
    private String audioBitrate = "128k";
}
