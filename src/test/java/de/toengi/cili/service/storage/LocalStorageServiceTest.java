package de.toengi.cili.service.storage;

import de.toengi.cili.config.FileStorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class LocalStorageServiceTest {

    @TempDir Path tempDir;
    LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        FileStorageConfig config = new FileStorageConfig();
        config.setBasePath(tempDir.toString());
        storageService = new LocalStorageService(config);
    }

    @Test
    void store_writesFileAndReturnsStoredName() throws IOException {
        byte[] data = "hello world".getBytes();
        String storedName = storageService.store(new ByteArrayInputStream(data), data.length);

        assertThat(storedName).hasSize(36);
        assertThat(storageService.exists(storedName)).isTrue();
    }

    @Test
    void retrieve_returnsStoredContent() throws IOException {
        byte[] data = "test content".getBytes();
        String storedName = storageService.store(new ByteArrayInputStream(data), data.length);

        try (InputStream is = storageService.retrieve(storedName)) {
            assertThat(is.readAllBytes()).isEqualTo(data);
        }
    }

    @Test
    void delete_removesFile() throws IOException {
        byte[] data = "to delete".getBytes();
        String storedName = storageService.store(new ByteArrayInputStream(data), data.length);

        storageService.delete(storedName);

        assertThat(storageService.exists(storedName)).isFalse();
    }

    @Test
    void retrieve_nonExistent_throws() {
        assertThatThrownBy(() -> storageService.retrieve("00000000-0000-0000-0000-000000000000"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void resolveLocalPath_returnsCorrectPath() throws IOException {
        byte[] data = "path test".getBytes();
        String storedName = storageService.store(new ByteArrayInputStream(data), data.length);

        Optional<Path> resolved = storageService.resolveLocalPath(storedName);
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).exists();
    }
}
