package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cili.whisper")
@Getter @Setter
public class WhisperConfig {
    private String scriptName = "transcribe_worker.py";
    private String glossaryPath;
    private String correctionsPath;
    private String language = "auto";
    private String model = "large-v3";
    private String device = "cuda";
    private String computeType = "int8_float16";
    private int beamSize = 5;
    private int cpuThreads = 0;
    private int timeoutMinutes = 30;
    // Cue-Darstellung (Tuning der Untertitel-Aufteilung; siehe transcribe_worker.py)
    private int maxChars = 84;
    private double maxDuration = 6.0;
    private double cuePad = 0.2;
    // Sync-Korrektur gegen Audio/Video-Clock-Mismatch (siehe transcribe_worker.py).
    // video|container|off. Bei Videoquellen wird das Originalvideo als --sync-ref
    // übergeben; bei reinen Audioquellen ist die Korrektur ein No-Op.
    private String sync = "video";
    // Stille-Schwellwert: Chunks mit höherer Stille-Wahrscheinlichkeit werden übersprungen.
    // 0.6 = faster-whisper-Standard; niedriger → mehr Halluzinationen bei Stille.
    private double noSpeechThreshold = 0.6;
}
