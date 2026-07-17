package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.NllbConfig;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SubtitleTranslationServiceTest {

    @Mock ProcessingJobService jobService;
    @Mock SubtitleTrackRepository subtitleTrackRepository;
    @Mock ProcessingJobRepository processingJobRepository;
    @Mock Executor translationExecutor;
    @Mock LanguageOptionService languageOptionService;

    NllbConfig nllbConfig;
    CiliGlobalConfig global;
    SubtitleTranslationService service;

    @BeforeEach
    void setUp() {
        nllbConfig = new NllbConfig();
        nllbConfig.setScriptName("translate_worker.py");
        nllbConfig.setModelPath("/opt/cili/nllb/nllb-600M");
        nllbConfig.setDevice("cpu");
        nllbConfig.setComputeType("int8");
        nllbConfig.setBeamSize(4);
        nllbConfig.setBatchSize(32);
        nllbConfig.setTimeoutMinutes(10);

        global = new CiliGlobalConfig();
        global.setPythonPath("python3");
        global.setScriptsDir("/opt/cili/scripts");

        service = new SubtitleTranslationService(
            jobService, subtitleTrackRepository, processingJobRepository,
            nllbConfig, global, translationExecutor, languageOptionService);
    }

    @Test
    void buildCommand_usesGlobalPythonPath() {
        global.setPythonPath("/usr/bin/python3.11");
        List<String> cmd = service.buildCommand(
            Path.of("/tmp/in.vtt"), Path.of("/tmp/out.vtt"), "de", "pl");
        assertThat(cmd.get(0)).isEqualTo("/usr/bin/python3.11");
    }

    @Test
    void buildCommand_containsAllRequiredArguments() {
        Path in = Path.of("/tmp/in.vtt");
        Path out = Path.of("/tmp/out.vtt");
        List<String> cmd = service.buildCommand(in, out, "de", "pl");

        assertThat(cmd)
                .containsSequence("--input", in.toString())
                .containsSequence("--output", out.toString())
                .containsSequence("--source", "de")
                .containsSequence("--target", "pl")
                .containsSequence("--model", "/opt/cili/nllb/nllb-600M")
                .containsSequence("--device", "cpu")
                .containsSequence("--compute-type", "int8")
                .containsSequence("--beam-size", "4")
                .containsSequence("--batch-size", "32");
    }

    @Test
    void toVtt_vttTrackReturnedUnchanged() {
        SubtitleTrack track = SubtitleTrack.builder()
            .format(SubtitleFormat.VTT)
            .textContent("WEBVTT\n\n1\n00:00:01.000 --> 00:00:03.000\nHello\n")
            .build();
        String result = service.toVtt(track);
        assertThat(result).startsWith("WEBVTT").contains("00:00:01.000");
    }

    @Test
    void toVtt_srtConvertedToVttFormat() {
        SubtitleTrack track = SubtitleTrack.builder()
            .format(SubtitleFormat.SRT)
            .textContent("1\n00:00:01,000 --> 00:00:03,000\nHallo Welt\n")
            .build();
        String result = service.toVtt(track);
        assertThat(result)
                .startsWith("WEBVTT")
                .contains("00:00:01.000 --> 00:00:03.000")
                .contains("Hallo Welt")
                .doesNotContain("00:00:01,000");
    }
}
