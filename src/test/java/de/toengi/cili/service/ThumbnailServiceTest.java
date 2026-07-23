package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Thumbnail;
import de.toengi.cili.model.enums.StorageType;
import de.toengi.cili.model.enums.ThumbnailStatus;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.ThumbnailRepository;
import de.toengi.cili.service.storage.StorageService;
import de.toengi.cili.util.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThumbnailServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    ThumbnailRepository thumbnailRepository;
    @Mock
    ResourceRepository resourceRepository;
    @Mock
    StorageService storageService;
    @Mock
    CommandRunner commandRunner;
    @Mock
    PlatformTransactionManager txManager;
    @Mock
    LibreOfficeConversionService libreOfficeConversionService;

    private FileStorageConfig config;
    private ThumbnailService thumbnailService;

    @BeforeEach
    void setUp() {
        config = new FileStorageConfig();
        config.setBasePath(tempDir.toString());
        config.setFfmpegPath("/usr/bin/ffmpeg");

        lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        thumbnailService = new ThumbnailService(
                thumbnailRepository, resourceRepository, storageService, config, commandRunner, txManager,
                libreOfficeConversionService);
    }

    // --- processUpload ---

    @Test
    void processUpload_nonVideo_doesNothing() {
        thumbnailService.processUpload(1L, "application/pdf");
        verifyNoInteractions(thumbnailRepository);
    }

    @Test
    void processUpload_resourceNotFound_doesNothing() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.empty());
        thumbnailService.processUpload(1L, "video/mp4");
        verifyNoInteractions(thumbnailRepository);
    }

    @Test
    void processUpload_ffmpegSuccess_setsDoneStatus() throws Exception {
        Resource resource = resource(1L, "abc");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        Thumbnail pending = Thumbnail.builder().id(10L).resourceId(1L)
                .status(ThumbnailStatus.PROCESSING).build();
        when(thumbnailRepository.findByResourceId(1L)).thenReturn(Optional.empty());
        when(thumbnailRepository.save(any())).thenReturn(pending);

        // Simulate local path exists
        Path fakeInput = tempDir.resolve("abc");
        Files.writeString(fakeInput, "fake video");
        when(storageService.resolveLocalPath("abc")).thenReturn(Optional.of(fakeInput));

        // FFmpeg always succeeds; create fake output files
        when(commandRunner.run(anyList())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String outputPath = cmd.getLast();
            Files.createDirectories(Path.of(outputPath).getParent());
            Files.writeString(Path.of(outputPath), "fake jpg");
            return 0;
        });

        thumbnailService.processUpload(1L, "video/mp4");

        verify(thumbnailRepository, atLeastOnce()).save(argThat(t ->
                t.getStatus() == ThumbnailStatus.DONE && t.getSmallPath() != null));
    }

    @Test
    void processUpload_ffmpegFailure_setsFailedStatus() throws Exception {
        Resource resource = resource(2L, "def");
        when(resourceRepository.findById(2L)).thenReturn(Optional.of(resource));

        Thumbnail pending = Thumbnail.builder().id(20L).resourceId(2L)
                .status(ThumbnailStatus.PROCESSING).build();
        when(thumbnailRepository.findByResourceId(2L)).thenReturn(Optional.empty());
        when(thumbnailRepository.save(any())).thenReturn(pending);

        Path fakeInput = tempDir.resolve("def");
        Files.writeString(fakeInput, "bad video");
        when(storageService.resolveLocalPath("def")).thenReturn(Optional.of(fakeInput));
        when(commandRunner.run(anyList())).thenReturn(1); // FFmpeg fails

        thumbnailService.processUpload(2L, "video/mp4");

        verify(thumbnailRepository, atLeastOnce()).save(argThat(t ->
                t.getStatus() == ThumbnailStatus.FAILED));
    }

    @Test
    void processUpload_noLocalPath_setsFailedStatus() {
        Resource resource = resource(3L, "ghi");
        when(resourceRepository.findById(3L)).thenReturn(Optional.of(resource));

        Thumbnail pending = Thumbnail.builder().id(30L).resourceId(3L)
                .status(ThumbnailStatus.PROCESSING).build();
        when(thumbnailRepository.findByResourceId(3L)).thenReturn(Optional.empty());
        when(thumbnailRepository.save(any())).thenReturn(pending);

        when(storageService.resolveLocalPath("ghi")).thenReturn(Optional.empty());

        thumbnailService.processUpload(3L, "video/mp4");

        verify(thumbnailRepository, atLeastOnce()).save(argThat(t ->
                t.getStatus() == ThumbnailStatus.FAILED));
        verifyNoInteractions(commandRunner);
    }

    // --- helpers ---

    private Resource resource(Long id, String storedName) {
        return Resource.builder()
                .id(id).folderId(10L).originalName("video.mp4")
                .storedName(storedName).mimeType("video/mp4")
                .size(100L).uploaderId(1L).storageType(StorageType.LOCAL)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
