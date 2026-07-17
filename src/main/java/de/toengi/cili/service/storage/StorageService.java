package de.toengi.cili.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

public interface StorageService {
    /** Stores data and returns the UUID stored name; {@code size} is a hint for pre-allocation and may be ignored. */
    String store(InputStream data, long size) throws IOException;
    InputStream retrieve(String storedName) throws IOException;
    /** Overwrites an existing stored file in-place. */
    void update(String storedName, InputStream data) throws IOException;
    void delete(String storedName) throws IOException;
    boolean exists(String storedName);
    /** Returns empty for non-local implementations (e.g. MinIO). */
    Optional<Path> resolveLocalPath(String storedName);

    /** Gibt den absoluten Zielpfad zurück, unter dem eine Datei mit diesem Namen gespeichert wird. */
    Path resolveStoragePath(String storedName);
}
