package de.toengi.cili.service;

import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.model.entity.ProcessingJob;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoTranscodeServiceTest {

    @Mock
    ResourceRepository resourceRepo;
    @Mock
    StorageService storageService;
    @Mock
    ProcessingJobService jobService;
    @Mock
    CommandRunner commandRunner;
    FfmpegTranscodeConfig ffmpegConfig;
    FileStorageConfig storageConfig;
    VideoTranscodeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ffmpegConfig = new FfmpegTranscodeConfig();
        ffmpegConfig.setTargetHeight(720);
        ffmpegConfig.setVideoCodec("libx264");
        ffmpegConfig.setAudioCodec("aac");
        ffmpegConfig.setCrf(23);
        ffmpegConfig.setPreset("medium");
        ffmpegConfig.setMaxBitrate("4000k");
        ffmpegConfig.setTimeoutMinutes(1);
        storageConfig = new FileStorageConfig();
        storageConfig.setFfmpegPath("/usr/bin/ffmpeg");
        service = new VideoTranscodeService(
                resourceRepo, storageService, jobService, commandRunner,
                ffmpegConfig, storageConfig);
    }

    @Test
    void buildFfmpegCommand_containsCodecAndCrfAndScale() {
        List<String> cmd = service.buildFfmpegCommand(
                Path.of("/input/video.mp4"),
                Path.of("/output/video.mp4"),
                ffmpegConfig);

        int vcodecIdx = cmd.indexOf("-vcodec");
        assertThat(vcodecIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(vcodecIdx + 1)).isEqualTo("libx264");

        int acodecIdx = cmd.indexOf("-acodec");
        assertThat(acodecIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(acodecIdx + 1)).isEqualTo("aac");

        int profileIdx = cmd.indexOf("-profile:a");
        assertThat(profileIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(profileIdx + 1)).isEqualTo("aac_low");

        int crfIdx = cmd.indexOf("-crf");
        assertThat(crfIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(crfIdx + 1)).isEqualTo("23");

        assertThat(cmd).anyMatch(s -> s.contains("scale=") && s.contains("720"));
    }

    @Test
    void execute_onFfmpegSuccess_updatesResourceStoredName() throws Exception {
        Resource resource = Resource.builder().id(1L).storedName("original-uuid")
                .mimeType("video/mp4").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(resource));
        Path fakeInput = Files.createTempFile("input", ".mp4");
        when(storageService.resolveLocalPath("original-uuid")).thenReturn(Optional.of(fakeInput));

        Path tempDir = Files.createTempDirectory("cili-ffmpeg");
        ffmpegConfig.setTempDir(tempDir.toString());

        // FFmpeg-Erfolg simulieren: gibt 0 zurück und wir erstellen die Output-Datei
        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outPath = cmd.get(cmd.size() - 1);
            Files.writeString(Path.of(outPath), "fake-video-data");
            return 0;
        });

        // resolveStoragePath zurückgeben
        when(storageService.resolveStoragePath(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return tempDir.resolve(name + "-final");
        });

        ProcessingJob job = ProcessingJob.builder().id(1L).resourceId(1L)
                .attempts(0).maxAttempts(3).build();

        service.execute(job);

        verify(resourceRepo).save(argThat(r -> !r.getStoredName().equals("original-uuid")));
        verify(jobService).markDone(eq(job), anyString());

        Files.deleteIfExists(fakeInput);
    }
}
