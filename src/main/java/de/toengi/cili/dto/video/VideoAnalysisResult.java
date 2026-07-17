package de.toengi.cili.dto.video;

public record VideoAnalysisResult(
    int width,
    int height,
    String videoCodec,
    String audioCodec,
    long bitrate,
    double durationSeconds,
    TranscodeAction action
) {
    public enum TranscodeAction {
        TRANSCODE,
        REMUX,
        SKIP
    }

    public boolean requiresProcessing() {
        return action == TranscodeAction.TRANSCODE || action == TranscodeAction.REMUX;
    }
}
