package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WavExtractServiceTest {

    WavExtractService service;
    FileStorageConfig storageConfig;
    FfmpegTranscodeConfig ffmpegConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        storageConfig = new FileStorageConfig();
        storageConfig.setFfmpegPath("/usr/bin/ffmpeg");
        ffmpegConfig = new FfmpegTranscodeConfig();
        ffmpegConfig.setTempDir("/tmp");
        // Null-Konstruktor ist OK für buildWavCommand-Test
        service = new WavExtractService(null, null, null, null, storageConfig, ffmpegConfig);
    }

    @Test
    void buildWavCommand_outputsMono16kPcm() {
        Path outputPath = Path.of("/output/audio.wav");
        List<String> cmd = service.buildWavCommand(
            Path.of("/input/video.mp4"),
            outputPath);

        assertThat(cmd)
                .containsSequence("-ac", "1")
                .containsSequence("-ar", "16000")
                .containsSequence("-acodec", "pcm_s16le")
                .contains(outputPath.toString());
    }

    @Test
    void execute_onSuccess_marksDoneAndReturnsWavPath() throws Exception {
        ProcessingJobService jobService = mock(ProcessingJobService.class);
        ResourceRepository resourceRepo = mock(ResourceRepository.class);
        StorageService storageService = mock(StorageService.class);
        CommandRunner commandRunner = mock(CommandRunner.class);

        FfmpegTranscodeConfig cfg = new FfmpegTranscodeConfig();
        Path tempDir = Files.createTempDirectory("cili-wav-test");
        cfg.setTempDir(tempDir.toString());
        FileStorageConfig sCfg = new FileStorageConfig();
        sCfg.setFfmpegPath("/usr/bin/ffmpeg");

        WavExtractService svc = new WavExtractService(
            resourceRepo, storageService, jobService, commandRunner, sCfg, cfg);

        Resource resource = Resource.builder().id(1L).storedName("video.mp4").build();
        Path fakeInput = Files.createTempFile("input", ".mp4");
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(resource));
        when(storageService.resolveLocalPath("video.mp4")).thenReturn(Optional.of(fakeInput));

        // FFmpeg simulieren: erzeugt WAV-Ausgabedatei
        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outPath = cmd.get(cmd.size() - 1);
            Files.writeString(Path.of(outPath), "RIFF");
            return 0;
        });

        ProcessingJob job = ProcessingJob.builder().id(1L).resourceId(1L).attempts(0).maxAttempts(3).build();
        Path result = svc.execute(job);

        assertThat(result).exists();
        verify(jobService).markDone(eq(job), anyString());

        Files.deleteIfExists(fakeInput);
    }
}
