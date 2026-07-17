package de.toengi.cili.util;

public class RangeUtils {

    private static final long DEFAULT_CHUNK = 1024 * 1024L; // 1 MB

    /**
     * Parses the first byte range from a Range header value.
     * Returns [start, end] (both inclusive) or null if header is absent/invalid.
     * Clamps end to totalLength - 1.
     */
    public static long[] parseFirstRange(String rangeHeader, long totalLength) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) return null;
        String spec = rangeHeader.substring(6);
        int dash = spec.indexOf('-');
        if (dash < 0) return null;

        String startStr = spec.substring(0, dash).trim();
        String endStr   = spec.substring(dash + 1).trim();

        try {
            long start;
            long end;
            if (startStr.isEmpty()) {
                // suffix range: bytes=-N  →  last N bytes
                if (endStr.isEmpty()) return null;
                long suffix = Long.parseLong(endStr);
                if (suffix <= 0) return null;
                start = Math.max(0, totalLength - suffix);
                end   = totalLength - 1;
            } else {
                start = Long.parseLong(startStr);
                if (start < 0) return null;
                if (start >= totalLength) return null;
                end = endStr.isEmpty()
                        ? Math.min(start + DEFAULT_CHUNK - 1, totalLength - 1)
                        : Long.parseLong(endStr);
            }
            end = Math.min(end, totalLength - 1);
            if (start > end) return null;
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Convenience: length of the range returned by parseFirstRange. */
    public static long length(long[] range) {
        return range[1] - range[0] + 1;
    }
}
