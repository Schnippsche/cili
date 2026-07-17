package de.toengi.cili.util;

public class MimeTypeUtils {

    private MimeTypeUtils() {}

    public static boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public static boolean isAudio(String mimeType) {
        return mimeType != null && mimeType.startsWith("audio/");
    }

    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public static boolean isDocument(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.equals("application/pdf")
                || mimeType.startsWith("application/vnd.openxmlformats")
                || mimeType.startsWith("application/vnd.ms-")
                || mimeType.equals("application/msword")
                || mimeType.equals("application/vnd.oasis.opendocument.text");
    }

    public static boolean isText(String mimeType) {
        return mimeType != null && mimeType.startsWith("text/");
    }

    public static boolean isSubtitle(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".srt") || lower.endsWith(".vtt");
    }
}
