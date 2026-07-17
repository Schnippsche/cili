package de.toengi.cili.util;

public class FileNameUtils {

    private FileNameUtils() {}

    /** Returns the file extension including the dot, e.g. ".mp4". Empty string if none. */
    public static String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot);
    }

    /** Returns the base name without extension or directory separators. */
    public static String getBaseName(String filename) {
        if (filename == null) return "";
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = filename.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Replaces characters unsafe for filenames with underscores. */
    public static String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
