package de.toengi.cili.service;

import de.toengi.cili.config.AudioConfig;
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

class AudioNormalizeServiceTest {

    @Mock
    ResourceRepository resourceRepo;
    @Mock
    StorageService storageService;
    @Mock
    ProcessingJobService jobService;
    @Mock
    CommandRunner commandRunner;
    FileStorageConfig storageConfig;
    FfmpegTranscodeConfig ffmpegConfig;
    AudioConfig audioConfig;
    AudioNormalizeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        storageConfig = new FileStorageConfig();
        storageConfig.setFfmpegPath("/usr/bin/ffmpeg");
        ffmpegConfig = new FfmpegTranscodeConfig();
        audioConfig = new AudioConfig();
        service = new AudioNormalizeService(
                resourceRepo, storageService, jobService, commandRunner, storageConfig, ffmpegConfig, audioConfig);
    }

    @Test
    void execute_onSuccess_updatesMimeTypeAndExtensionToMp3() throws Exception {
        Resource resource = Resource.builder().id(1L).storedName("original-uuid")
                .originalName("Song.ogg").mimeType("audio/ogg").build();
        when(resourceRepo.findById(1L)).thenReturn(Optional.of(resource));
        Path fakeInput = Files.createTempFile("input", ".ogg");
        when(storageService.resolveLocalPath("original-uuid")).thenReturn(Optional.of(fakeInput));

        Path tempDir = Files.createTempDirectory("cili-ffmpeg");
        ffmpegConfig.setTempDir(tempDir.toString());

        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outPath = cmd.get(cmd.size() - 1);
            Files.writeString(Path.of(outPath), "fake-mp3-data");
            return 0;
        });

        when(storageService.resolveStoragePath(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return tempDir.resolve(name + "-final");
        });

        ProcessingJob job = ProcessingJob.builder().id(1L).resourceId(1L)
                .attempts(0).maxAttempts(3).build();

        service.execute(job);

        verify(resourceRepo).save(argThat(r ->
                "audio/mpeg".equals(r.getMimeType()) && "Song.mp3".equals(r.getOriginalName())));
        verify(jobService).markDone(eq(job), anyString());

        Files.deleteIfExists(fakeInput);
    }
}
