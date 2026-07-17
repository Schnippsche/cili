package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.WhisperConfig;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WhisperTranscriptionServiceTest {

    private WhisperTranscriptionService makeService(WhisperConfig cfg) {
        return makeService(cfg, new CiliGlobalConfig());
    }

    private WhisperTranscriptionService makeService(WhisperConfig cfg, CiliGlobalConfig global) {
        return new WhisperTranscriptionService(
            mock(ProcessingJobService.class),
            mock(SubtitleTrackRepository.class),
            mock(ResourceRepository.class),
            mock(StorageService.class),
            cfg,
            global,
            mock(LanguageOptionService.class));
    }

    @Test
    void buildCommand_allArgs_correctOrder() {
        WhisperConfig cfg = new WhisperConfig();
        cfg.setLanguage("de");
        cfg.setModel("large-v3");
        cfg.setDevice("cuda");
        cfg.setComputeType("int8_float16");
        cfg.setBeamSize(3);
        cfg.setGlossaryPath("/var/cili/glossar.txt");
        cfg.setCorrectionsPath("/var/cili/corrections.txt");

        CiliGlobalConfig global = new CiliGlobalConfig();
        global.setPythonPath("python3");
        global.setScriptsDir("/opt/cili");

        WhisperTranscriptionService svc = makeService(cfg, global);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd.get(0)).isEqualTo("python3");
        assertThat(cmd.get(1)).isEqualTo(Path.of("/opt/cili", "transcribe_worker.py").toString());
        assertThat(cmd)
                .containsSequence("--wav", Path.of("/tmp/audio.wav").toString())
                .containsSequence("--output", Path.of("/tmp/out.vtt").toString())
                .containsSequence("--lang", "de")
                .containsSequence("--model", "large-v3")
                .containsSequence("--device", "cuda")
                .containsSequence("--compute-type", "int8_float16")
                .containsSequence("--beam-size", "3")
                .containsSequence("--glossary", "/var/cili/glossar.txt")
                .containsSequence("--corrections", "/var/cili/corrections.txt");
    }

    @Test
    void buildCommand_autoLanguage_omitsLangPassesLangOutput() {
        WhisperConfig cfg = new WhisperConfig(); // default: language="auto"
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd).doesNotContain("--lang");
        assertThat(cmd).containsSequence("--lang-output", Path.of("/tmp/lang.json").toString());
    }

    @Test
    void buildCommand_fixedLanguage_passesLangAndLangOutput() {
        WhisperConfig cfg = new WhisperConfig();
        cfg.setLanguage("en");
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd).containsSequence("--lang", "en");
        assertThat(cmd).containsSequence("--lang-output", Path.of("/tmp/lang.json").toString());
    }

    @Test
    void buildCommand_includesCueTuningDefaults() {
        WhisperConfig cfg = new WhisperConfig();
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd)
                .containsSequence("--max-chars", "84")
                .containsSequence("--max-duration", "6.0")
                .containsSequence("--cue-pad", "0.2");
    }

    @Test
    void buildCommand_includesCueTuningOverrides() {
        WhisperConfig cfg = new WhisperConfig();
        cfg.setMaxChars(42);
        cfg.setMaxDuration(4.5);
        cfg.setCuePad(0.3);
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd)
                .containsSequence("--max-chars", "42")
                .containsSequence("--max-duration", "4.5")
                .containsSequence("--cue-pad", "0.3");
    }

    @Test
    void buildCommand_syncDefault_passesVideoAndNoRefWhenAbsent() {
        WhisperConfig cfg = new WhisperConfig();
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd).containsSequence("--sync", "video").doesNotContain("--sync-ref");
    }

    @Test
    void buildCommand_syncRefPresent_addsSyncRef() {
        WhisperConfig cfg = new WhisperConfig();
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.of(Path.of("/var/cili/data/video.mp4")),
            Path.of("/tmp/lang.json"));

        assertThat(cmd)
                .containsSequence("--sync", "video")
                .containsSequence("--sync-ref", Path.of("/var/cili/data/video.mp4").toString());
    }

    @Test
    void buildCommand_syncOff_omitsSyncRefEvenIfPresent() {
        WhisperConfig cfg = new WhisperConfig();
        cfg.setSync("off");
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.of(Path.of("/var/cili/data/video.mp4")),
            Path.of("/tmp/lang.json"));

        assertThat(cmd).containsSequence("--sync", "off").doesNotContain("--sync-ref");
    }

    @Test
    void buildCommand_noGlossary_omitsFlag() {
        WhisperConfig cfg = new WhisperConfig();
        cfg.setGlossaryPath(null);
        cfg.setCorrectionsPath(null);
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd).isNotEmpty().doesNotContain("--glossary").doesNotContain("--corrections");
    }

    @Test
    void buildCommand_emptyGlossaryPath_omitsFlag() {
        WhisperConfig cfg = new WhisperConfig();
        cfg.setGlossaryPath("   ");
        cfg.setCorrectionsPath("   ");
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd).isNotEmpty().doesNotContain("--glossary").doesNotContain("--corrections");
    }

    @Test
    void buildCommand_noSpeechThreshold_default() {
        WhisperConfig cfg = new WhisperConfig();
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd).containsSequence("--no-speech-threshold", "0.6");
    }

    @Test
    void buildCommand_noSpeechThreshold_override() {
        WhisperConfig cfg = new WhisperConfig();
        cfg.setNoSpeechThreshold(0.3);
        WhisperTranscriptionService svc = makeService(cfg);

        List<String> cmd = svc.buildCommand(
            Path.of("/tmp/audio.wav"),
            Path.of("/tmp/out.vtt"),
            Optional.empty(),
            Path.of("/tmp/lang.json"));

        assertThat(cmd).containsSequence("--no-speech-threshold", "0.3");
    }
}
