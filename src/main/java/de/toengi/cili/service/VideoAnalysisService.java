package de.toengi.cili.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.dto.video.VideoAnalysisResult;
import de.toengi.cili.dto.video.VideoAnalysisResult.TranscodeAction;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoAnalysisService {

    private static final Set<String> WEB_VIDEO_CODECS = Set.of("h264", "av1");
    private static final Set<String> WEB_AUDIO_CODECS = Set.of("aac", "mp3", "opus");
    // HE-AAC and ELD are AAC sub-profiles not supported by most browsers; force transcode to AAC-LC
    private static final Set<String> UNSUPPORTED_AAC_PROFILES = Set.of("he-aac", "he-aac v2", "eld");
    public static final String UNKNOWN = "unknown";

    private final ResourceRepository resourceRepository;
    private final StorageService storageService;
    private final ProcessingJobService jobService;
    private final FfmpegTranscodeConfig ffmpegConfig;
    private final FileStorageConfig storageConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoAnalysisResult analyze(Long resourceId, ProcessingJob job)
            throws IOException, InterruptedException {
        Resource resource = resourceRepository.findById(resourceId)
            .orElseThrow(() -> new IllegalStateException("Resource not found: " + resourceId));
        Path inputPath = storageService.resolveLocalPath(resource.getStoredName())
            .orElseThrow(() -> new IllegalStateException("File not stored locally: " + resourceId));

        String ffprobeJson = runFfprobe(inputPath);
        VideoAnalysisResult result = parseAnalysisOutput(ffprobeJson, ffmpegConfig);
        log.info("Analysis for resource {}: {}x{} {} → action={}",
            resourceId, result.width(), result.height(), result.videoCodec(), result.action());
        return result;
    }

    VideoAnalysisResult parseAnalysisOutput(String ffprobeJson, FfmpegTranscodeConfig cfg)
            throws IOException {
        JsonNode root = objectMapper.readTree(ffprobeJson);

        String videoCodec = UNKNOWN;
        String audioCodec = UNKNOWN;
        String audioProfile = "";
        int width = 0;
        int height = 0;

        for (JsonNode stream : root.path("streams")) {
            String codecType = stream.path("codec_type").asText();
            if ("video".equals(codecType)) {
                videoCodec = stream.path("codec_name").asText(UNKNOWN);
                width = stream.path("width").asInt(0);
                height = stream.path("height").asInt(0);
            } else if ("audio".equals(codecType)) {
                audioCodec = stream.path("codec_name").asText(UNKNOWN);
                audioProfile = stream.path("profile").asText("");
            }
        }

        long bitrate = root.path("format").path("bit_rate").asLong(0);
        double duration = root.path("format").path("duration").asDouble(0);
        TranscodeAction action = decideAction(height, videoCodec, audioCodec, audioProfile, cfg);
        return new VideoAnalysisResult(width, height, videoCodec, audioCodec, bitrate, duration, action);
    }

    private TranscodeAction decideAction(int height, String videoCodec,
                                          String audioCodec, String audioProfile,
                                          FfmpegTranscodeConfig cfg) {
        boolean needsScale = height > cfg.getTargetHeight();
        boolean needsVideoTranscode = !WEB_VIDEO_CODECS.contains(videoCodec);
        boolean needsAudioTranscode = !WEB_AUDIO_CODECS.contains(audioCodec)
                || ("aac".equals(audioCodec) && UNSUPPORTED_AAC_PROFILES.contains(audioProfile.toLowerCase()));

        if (needsScale || needsVideoTranscode || needsAudioTranscode) {
            return TranscodeAction.TRANSCODE;
        }
        return TranscodeAction.SKIP;
    }

    private String runFfprobe(Path input) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(
            storageConfig.getFfprobePath(),
            "-v", "quiet",
            "-print_format", "json",
            "-show_streams",
            "-show_format",
            input.toString()
        ));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String json;
        try (InputStream is = proc.getInputStream()) {
            json = new String(is.readAllBytes());
        }
        int rc = proc.waitFor();
        if (rc != 0) {
            throw new IOException("ffprobe exited with code " + rc);
        }
        return json;
    }
}
