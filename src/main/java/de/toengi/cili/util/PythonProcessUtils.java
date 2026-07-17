package de.toengi.cili.util;

import java.io.File;
import java.util.List;

/**
 * Hilfsmethoden für Python-Subprozesse: setzt UTF-8-Encoding, deaktiviert
 * stdout-Pufferung und legt das Arbeitsverzeichnis auf das Skript-Verzeichnis.
 */
public final class PythonProcessUtils {

    private PythonProcessUtils() {}

    /**
     * Setzt PYTHONIOENCODING, PYTHONUTF8 und PYTHONUNBUFFERED auf dem ProcessBuilder
     * und richtet das Arbeitsverzeichnis auf das Verzeichnis des Skripts ein.
     *
     * @param pb         der zu konfigurierende ProcessBuilder
     * @param scriptPath absoluter Pfad zum Python-Skript
     */
    public static void configure(ProcessBuilder pb, String scriptPath) {
        applyEnv(pb);
        File dir = new File(scriptPath).getParentFile();
        if (dir != null && dir.isDirectory()) {
            pb.directory(dir);
        }
    }

    /**
     * Setzt nur die Python-Umgebungsvariablen, ohne das Arbeitsverzeichnis zu ändern.
     * Nützlich wenn der Prozess kein Python-Skript mit festen Pfad startet (z.B. ffmpeg).
     */
    public static void applyEnv(ProcessBuilder pb) {
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8",       "1");
        pb.environment().put("PYTHONUNBUFFERED", "1");
    }

    /**
     * Erstellt einen vorkonfigurierten ProcessBuilder für ein Python-Skript.
     *
     * @param cmd        Kommandozeilenargumente (python, scriptPath, ...)
     * @param scriptPath Pfad zum Skript — bestimmt das Arbeitsverzeichnis
     */
    public static ProcessBuilder forScript(List<String> cmd, String scriptPath) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        configure(pb, scriptPath);
        return pb;
    }
}
