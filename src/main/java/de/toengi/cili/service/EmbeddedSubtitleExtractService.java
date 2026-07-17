package de.toengi.cili.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddedSubtitleExtractService {

    private static final Set<String> BITMAP_CODECS = Set.of(
        "dvd_subtitle", "dvb_subtitle", "dvb_teletext",
        "hdmv_pgs_subtitle", "pgssub", "xsub"
    );

    private static final Map<String, String> ISO639_2_TO_1 = Map.ofEntries(
        Map.entry("deu", "de"), Map.entry("ger", "de"),
        Map.entry("eng", "en"),
        Map.entry("fra", "fr"), Map.entry("fre", "fr"),
        Map.entry("spa", "es"),
        Map.entry("ita", "it"),
        Map.entry("jpn", "ja"),
        Map.entry("zho", "zh"), Map.entry("chi", "zh"),
        Map.entry("por", "pt"),
        Map.entry("rus", "ru"),
        Map.entry("ara", "ar"),
        Map.entry("kor", "ko"),
        Map.entry("nld", "nl"), Map.entry("dut", "nl"),
        Map.entry("pol", "pl"),
        Map.entry("tur", "tr"),
        Map.entry("swe", "sv"),
        Map.entry("nor", "no"),
        Map.entry("dan", "da"),
        Map.entry("fin", "fi"),
        Map.entry("ces", "cs"), Map.entry("cze", "cs"),
        Map.entry("hun", "hu"),
        Map.entry("ron", "ro"), Map.entry("rum", "ro"),
        Map.entry("hrv", "hr"),
        Map.entry("bul", "bg"),
        Map.entry("slk", "sk"), Map.entry("slo", "sk"),
        Map.entry("ukr", "uk"),
        Map.entry("heb", "he"),
        Map.entry("tha", "th"),
        Map.entry("vie", "vi"),
        Map.entry("ind", "id"),
        Map.entry("msa", "ms"), Map.entry("may", "ms")
    );

    private final FileStorageConfig config;
    private final StorageService storageService;
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final CommandRunner commandRunner;
    private final PlatformTransactionManager txManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record SubtitleStreamInfo(int globalIndex, String languageCode, String label) {}

    public int extractAndStore(Long resourceId, Path videoPath) {
        String json = runFfprobe(videoPath);
        List<SubtitleStreamInfo> streams = parseSubtitleStreams(json, objectMapper);
        if (streams.isEmpty()) return 0;

        int count = 0;
        for (SubtitleStreamInfo stream : streams) {
            if (subtitleTrackRepository.existsByResourceIdAndLanguageCode(resourceId, stream.languageCode())) {
                log.debug("[EMBEDDED-SUB] Skipping stream {} for resource {} — track for '{}' already exists",
                    stream.globalIndex(), resourceId, stream.languageCode());
                continue;
            }
            if (extractAndSave(resourceId, videoPath, stream)) count++;
        }
        if (count > 0) {
            log.info("[EMBEDDED-SUB] Extracted {} subtitle track(s) from resource {}", count, resourceId);
        }
        return count;
    }

    private boolean extractAndSave(Long resourceId, Path videoPath, SubtitleStreamInfo stream) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("cili-sub-", ".srt");
            List<String> cmd = List.of(
                config.getFfmpegPath(), "-y",
                "-i", videoPath.toString(),
                "-map", "0:" + stream.globalIndex(),
                "-f", "srt",
                tempFile.toString()
            );
            int rc = commandRunner.run(cmd);
            if (rc != 0 || !Files.exists(tempFile) || Files.size(tempFile) == 0) {
                log.warn("[EMBEDDED-SUB] ffmpeg extraction failed (rc={}) for stream {} of resource {}",
                    rc, stream.globalIndex(), resourceId);
                return false;
            }
            String srtContent = Files.readString(tempFile, StandardCharsets.UTF_8);
            byte[] srtBytes = srtContent.getBytes(StandardCharsets.UTF_8);
            String storedName = storageService.store(new ByteArrayInputStream(srtBytes), srtBytes.length);
            String label = stream.label() != null
                ? "Eingebettet (" + stream.label() + ")"
                : "Eingebettet (" + stream.languageCode() + ")";
            String textContent = srtContent.length() <= 500_000 ? srtContent : srtContent.substring(0, 500_000);
            new TransactionTemplate(txManager).execute(status -> {
                subtitleTrackRepository.save(SubtitleTrack.builder()
                    .resourceId(resourceId)
                    .languageCode(stream.languageCode())
                    .label(label)
                    .storedName(storedName)
                    .format(SubtitleFormat.SRT)
                    .textContent(textContent)
                    .build());
                return null;
            });
            log.info("[EMBEDDED-SUB] Saved stream {} as track lang='{}' for resource {}",
                stream.globalIndex(), stream.languageCode(), resourceId);
            return true;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("[EMBEDDED-SUB] Failed to extract stream {} for resource {}: {}",
                stream.globalIndex(), resourceId, e.getMessage());
            return false;
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ex) { /* ignore */ }
            }
        }
    }

    private String runFfprobe(Path videoPath) {
        ProcessBuilder pb = new ProcessBuilder(
            config.getFfprobePath(),
            "-v", "quiet",
            "-print_format", "json",
            "-show_streams",
            videoPath.toString()
        );
        pb.redirectErrorStream(true);
        Process p = null;
        try {
            p = pb.start();
            String output;
            try (InputStream is = p.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                log.warn("[EMBEDDED-SUB] ffprobe timed out for {}", videoPath.getFileName());
                return null;
            }
            return output;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[EMBEDDED-SUB] ffprobe interrupted for {}", videoPath.getFileName());
            return null;
        } catch (IOException e) {
            log.warn("[EMBEDDED-SUB] ffprobe failed for {}: {}", videoPath.getFileName(), e.getMessage());
            return null;
        } finally {
            if (p != null) p.destroyForcibly();
        }
    }

    static List<SubtitleStreamInfo> parseSubtitleStreams(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode streamsNode = root.path("streams");
            if (!streamsNode.isArray()) return List.of();

            List<SubtitleStreamInfo> result = new ArrayList<>();
            for (JsonNode s : streamsNode) {
                if (!"subtitle".equals(s.path("codec_type").asText())) continue;
                String codec = s.path("codec_name").asText("");
                if (BITMAP_CODECS.contains(codec)) continue;
                int index = s.path("index").asInt(-1);
                if (index < 0) continue;
                JsonNode tags = s.path("tags");
                String rawLang = tags.path("language").asText(null);
                String lang = normalizeLanguageCode(rawLang);
                String title = tags.path("title").asText(null);
                if (title != null && title.isBlank()) title = null;
                result.add(new SubtitleStreamInfo(index, lang, title));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    static String normalizeLanguageCode(String raw) {
        if (raw == null || raw.isBlank()) return "und";
        return ISO639_2_TO_1.getOrDefault(raw.toLowerCase(), raw.toLowerCase());
    }
}
