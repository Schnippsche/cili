package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.exception.ResourceNotFoundException;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.Thumbnail;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThumbnailServiceNoAclTest {

    @TempDir Path tempDir;

    @Mock ThumbnailRepository thumbnailRepository;
    @Mock ResourceRepository resourceRepository;
    @Mock StorageService storageService;
    @Mock CommandRunner commandRunner;
    @Mock PlatformTransactionManager txManager;

    private ThumbnailService service;

    @BeforeEach
    void setUp() {
        FileStorageConfig config = new FileStorageConfig();
        config.setBasePath(tempDir.toString());
        config.setFfmpegPath("/usr/bin/ffmpeg");
        lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ThumbnailService(
                thumbnailRepository, resourceRepository, storageService, config, commandRunner, txManager);
    }

    @Test
    void getThumbnailBytesNoAcl_returnsThumbnailBytes_whenDone() throws IOException {
        Path smallFile = tempDir.resolve("thumbnails/abc-small.jpg");
        Files.createDirectories(smallFile.getParent());
        Files.write(smallFile, new byte[]{1, 2, 3});

        Thumbnail thumb = new Thumbnail();
        thumb.setStatus(ThumbnailStatus.DONE);
        thumb.setSmallPath("thumbnails/abc-small.jpg");
        when(thumbnailRepository.findByResourceId(1L)).thenReturn(Optional.of(thumb));

        byte[] result = service.getThumbnailBytesNoAcl(1L, "small");

        assertThat(result).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void getThumbnailBytesNoAcl_fallsBackToOriginal_whenNoDoneThumb() throws IOException {
        Path tmpFile = Files.createTempFile(tempDir, "cili-test-", ".jpg");
        Files.write(tmpFile, new byte[]{9, 8});

        when(thumbnailRepository.findByResourceId(2L)).thenReturn(Optional.empty());
        Resource resource = new Resource();
        resource.setMimeType("image/jpeg");
        resource.setStoredName("abc.jpg");
        when(resourceRepository.findById(2L)).thenReturn(Optional.of(resource));
        when(storageService.resolveLocalPath("abc.jpg")).thenReturn(Optional.of(tmpFile));

        byte[] result = service.getThumbnailBytesNoAcl(2L, "small");

        assertThat(result).isEqualTo(new byte[]{9, 8});
    }

    @Test
    void getThumbnailBytesNoAcl_throws_whenResourceNotFound() {
        when(thumbnailRepository.findByResourceId(99L)).thenReturn(Optional.empty());
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getThumbnailBytesNoAcl(99L, "small"))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
