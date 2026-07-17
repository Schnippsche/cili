package de.toengi.cili.service.storage;

import de.toengi.cili.config.FileStorageConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final FileStorageConfig config;

    @Override
    public String store(InputStream data, long size) throws IOException {
        String storedName = UUID.randomUUID().toString();
        Path dest = resolvePath(storedName);
        Files.createDirectories(dest.getParent());
        Files.copy(data, dest, StandardCopyOption.REPLACE_EXISTING);
        return storedName;
    }

    @Override
    public InputStream retrieve(String storedName) throws IOException {
        Path path = resolvePath(storedName);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + storedName);
        }
        return Files.newInputStream(path);
    }

    @Override
    public void update(String storedName, InputStream data) throws IOException {
        Path path = resolvePath(storedName);
        if (!Files.exists(path)) {
            throw new IOException("File not found for update: " + storedName);
        }
        Files.copy(data, path, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete(String storedName) throws IOException {
        Files.deleteIfExists(resolvePath(storedName));
    }

    @Override
    public boolean exists(String storedName) {
        return Files.exists(resolvePath(storedName));
    }

    @Override
    public Optional<Path> resolveLocalPath(String storedName) {
        return Optional.of(resolvePath(storedName));
    }

    @Override
    public Path resolveStoragePath(String storedName) {
        return resolvePath(storedName);
    }

    private Path resolvePath(String storedName) {
        String prefix = storedName.length() >= 2 ? storedName.substring(0, 2) : storedName;
        return Paths.get(config.getBasePath(), "resources", prefix, storedName);
    }
}
