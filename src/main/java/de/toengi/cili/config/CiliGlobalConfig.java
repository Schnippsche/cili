package de.toengi.cili.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties("cili")
@Getter @Setter
public class CiliGlobalConfig {

    /** Python-Interpreter für alle Skripte; kann per Profil überschrieben werden. */
    private String pythonPath = "python3";

    /** Verzeichnis, in dem alle Python-Skripte liegen. */
    private String scriptsDir = "/opt/cili/scripts";

    /** Löst einen Dateinamen zum vollen Pfad im scriptsDir auf. */
    public String resolve(String name) {
        if (name == null || name.isBlank()) return null;
        return Path.of(scriptsDir, name).toString();
    }
}
