package de.toengi.cili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedSubtitleExtractServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void normalizeLanguageCode_convertsIso6392ToIso6391() {
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode("deu")).isEqualTo("de");
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode("eng")).isEqualTo("en");
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode("fra")).isEqualTo("fr");
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode("ger")).isEqualTo("de");
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode("fre")).isEqualTo("fr");
    }

    @Test
    void normalizeLanguageCode_returnsUnchangedWhenUnknown() {
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode("xyz")).isEqualTo("xyz");
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode("und")).isEqualTo("und");
    }

    @Test
    void normalizeLanguageCode_nullOrEmpty_returnsUnd() {
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode(null)).isEqualTo("und");
        assertThat(EmbeddedSubtitleExtractService.normalizeLanguageCode("")).isEqualTo("und");
    }

    @Test
    void parseSubtitleStreams_findsTextBasedStreams() {
        String json = """
            {
              "streams": [
                {"index": 0, "codec_type": "video", "codec_name": "h264", "tags": {}},
                {"index": 1, "codec_type": "audio", "codec_name": "aac", "tags": {}},
                {"index": 2, "codec_type": "subtitle", "codec_name": "subrip",
                 "tags": {"language": "deu", "title": "Deutsch"}},
                {"index": 3, "codec_type": "subtitle", "codec_name": "subrip",
                 "tags": {"language": "eng"}}
              ]
            }
            """;
        List<EmbeddedSubtitleExtractService.SubtitleStreamInfo> streams =
            EmbeddedSubtitleExtractService.parseSubtitleStreams(json, MAPPER);

        assertThat(streams).hasSize(2);
        assertThat(streams.get(0).globalIndex()).isEqualTo(2);
        assertThat(streams.get(0).languageCode()).isEqualTo("de");
        assertThat(streams.get(0).label()).isEqualTo("Deutsch");
        assertThat(streams.get(1).globalIndex()).isEqualTo(3);
        assertThat(streams.get(1).languageCode()).isEqualTo("en");
        assertThat(streams.get(1).label()).isNull();
    }

    @Test
    void parseSubtitleStreams_skipsBitmapCodecs() {
        String json = """
            {
              "streams": [
                {"index": 2, "codec_type": "subtitle", "codec_name": "dvd_subtitle", "tags": {}},
                {"index": 3, "codec_type": "subtitle", "codec_name": "hdmv_pgs_subtitle", "tags": {}},
                {"index": 4, "codec_type": "subtitle", "codec_name": "subrip",
                 "tags": {"language": "eng"}}
              ]
            }
            """;
        List<EmbeddedSubtitleExtractService.SubtitleStreamInfo> streams =
            EmbeddedSubtitleExtractService.parseSubtitleStreams(json, MAPPER);

        assertThat(streams).hasSize(1);
        assertThat(streams.get(0).globalIndex()).isEqualTo(4);
    }

    @Test
    void parseSubtitleStreams_emptyStreams_returnsEmptyList() {
        String json = """
            { "streams": [] }
            """;
        assertThat(EmbeddedSubtitleExtractService.parseSubtitleStreams(json, MAPPER)).isEmpty();
    }

    @Test
    void parseSubtitleStreams_noSubtitleStreams_returnsEmptyList() {
        String json = """
            {
              "streams": [
                {"index": 0, "codec_type": "video", "codec_name": "h264", "tags": {}},
                {"index": 1, "codec_type": "audio", "codec_name": "aac", "tags": {}}
              ]
            }
            """;
        assertThat(EmbeddedSubtitleExtractService.parseSubtitleStreams(json, MAPPER)).isEmpty();
    }

    @Test
    void parseSubtitleStreams_missingLanguageTag_usesUnd() {
        String json = """
            {
              "streams": [
                {"index": 2, "codec_type": "subtitle", "codec_name": "ass", "tags": {}}
              ]
            }
            """;
        List<EmbeddedSubtitleExtractService.SubtitleStreamInfo> streams =
            EmbeddedSubtitleExtractService.parseSubtitleStreams(json, MAPPER);

        assertThat(streams).hasSize(1);
        assertThat(streams.get(0).languageCode()).isEqualTo("und");
    }

    @Test
    void parseSubtitleStreams_invalidJson_returnsEmptyList() {
        assertThat(EmbeddedSubtitleExtractService.parseSubtitleStreams("not json", MAPPER)).isEmpty();
        assertThat(EmbeddedSubtitleExtractService.parseSubtitleStreams(null, MAPPER)).isEmpty();
    }

    // ---- Extraction tests ----

    private EmbeddedSubtitleExtractService buildService(
            CommandRunner runner,
            StorageService storage,
            SubtitleTrackRepository repo) {
        FileStorageConfig cfg = new FileStorageConfig();
        cfg.setFfmpegPath("/usr/bin/ffmpeg");
        cfg.setFfprobePath("/usr/bin/ffprobe");
        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = Mockito.mock(TransactionStatus.class);
        Mockito.when(txManager.getTransaction(ArgumentMatchers.any())).thenReturn(txStatus);
        return new EmbeddedSubtitleExtractService(cfg, storage, repo, runner, txManager);
    }

    @Test
    void extractAndStore_returnsZero_whenFfprobeOutputIsEmpty() throws Exception {
        CommandRunner runner = Mockito.mock(CommandRunner.class);
        StorageService storage = Mockito.mock(StorageService.class);
        SubtitleTrackRepository repo = Mockito.mock(SubtitleTrackRepository.class);

        EmbeddedSubtitleExtractService svc = buildService(runner, storage, repo);
        java.nio.file.Path fakeVideo = java.nio.file.Files.createTempFile("test-video", ".mp4");
        try {
            // ffprobe will fail (not a real video) → 0 streams → 0 count
            int result = svc.extractAndStore(1L, fakeVideo);
            assertThat(result).isEqualTo(0);
            Mockito.verify(storage, Mockito.never())
                .store(ArgumentMatchers.any(), ArgumentMatchers.anyLong());
        } finally {
            java.nio.file.Files.deleteIfExists(fakeVideo);
        }
    }

    @Test
    void extractAndStore_skipsDuplicateLanguageCode() throws Exception {
        CommandRunner runner = Mockito.mock(CommandRunner.class);
        StorageService storage = Mockito.mock(StorageService.class);
        SubtitleTrackRepository repo = Mockito.mock(SubtitleTrackRepository.class);

        // "de" already exists
        Mockito.when(repo.existsByResourceIdAndLanguageCode(1L, "de")).thenReturn(true);

        EmbeddedSubtitleExtractService svc = buildService(runner, storage, repo);
        java.nio.file.Path fakeVideo = java.nio.file.Files.createTempFile("test-video2", ".mp4");
        try {
            int result = svc.extractAndStore(1L, fakeVideo);
            assertThat(result).isEqualTo(0);
            Mockito.verify(storage, Mockito.never())
                .store(ArgumentMatchers.any(), ArgumentMatchers.anyLong());
        } finally {
            java.nio.file.Files.deleteIfExists(fakeVideo);
        }
    }
}
