package de.toengi.cili.util;

import de.toengi.cili.model.enums.SubtitleFormat;

public final class SubtitleConverter {

    private SubtitleConverter() {}

    public static String toVtt(String content, SubtitleFormat source) {
        if (source == SubtitleFormat.VTT) return content;
        // SRT → VTT: add header, replace commas in timestamps, drop sequence numbers
        StringBuilder sb = new StringBuilder("WEBVTT\n");
        for (String block : content.trim().split("\\n\\n+")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;
            sb.append("\n");
            for (String line : trimmed.split("\\n")) {
                if (line.trim().matches("\\d+")) continue; // skip SRT sequence number
                sb.append(line.replaceAll("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})", "$1.$2")).append("\n");
            }
        }
        return sb.toString();
    }

    public static String toSrt(String content, SubtitleFormat source) {
        if (source == SubtitleFormat.SRT) return content;
        // VTT → SRT: skip WEBVTT/NOTE blocks, find timestamp line, add sequence numbers
        StringBuilder sb = new StringBuilder();
        int seq = 1;
        for (String block : content.trim().split("\\n\\n+")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;
            String[] lines = trimmed.split("\\n");
            int tsIndex = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) { tsIndex = i; break; }
            }
            if (tsIndex < 0) continue; // WEBVTT header, NOTE, metadata — skip
            sb.append(seq++).append("\n");
            for (int i = tsIndex; i < lines.length; i++) {
                sb.append(lines[i].replaceAll("(\\d{2}:\\d{2}:\\d{2})\\.(\\d{3})", "$1,$2")).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String toPlainText(String content) {
        StringBuilder sb = new StringBuilder();
        for (String raw : content.split("\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("WEBVTT")) continue;
            if (line.startsWith("NOTE")) continue;
            if (line.matches("\\d+")) continue;  // SRT sequence number
            if (line.matches("\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->.*")) continue; // timestamp
            // Strip inline tags (<i>, <b>, {…})
            line = line.replaceAll("<[^>]+>", "").replaceAll("\\{[^}]+}", "").trim();
            if (!line.isEmpty()) sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }
}
