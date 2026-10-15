package com.shop.catalog.internal.image.storage.filesystem;

import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import com.shop.catalog.internal.image.storage.ObjectStorage;
import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.catalog.images.storage", name = "type", havingValue = "filesystem")
@Slf4j
public class FileSystemObjectStorage implements ObjectStorage {

    private final CatalogImageProperties properties;
    private Path storageRoot;

    public FileSystemObjectStorage(CatalogImageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        storageRoot =
                properties.getStorage().getFilesystemRoot().toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new ObjectStorageException("Cannot initialize filesystem object storage", exception);
        }
    }

    @Override
    public void store(ObjectStorageUpload upload) {
        Path target = resolveSafely(upload.objectKey());
        Path temporaryFile = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        boolean targetReserved = false;
        boolean stored = false;
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporaryFile, upload.content(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.createFile(target);
            targetReserved = true;
            moveAtomicallyWhenSupported(temporaryFile, target);
            stored = true;
        } catch (IOException exception) {
            throw new ObjectStorageException("Cannot store object " + upload.objectKey(), exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
            if (targetReserved && !stored) {
                deleteTemporaryFile(target);
            }
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolveSafely(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new ObjectStorageException("Cannot delete object " + objectKey, exception);
        }
    }

    @Override
    public Optional<URI> createReadUrl(String objectKey) {
        resolveSafely(objectKey);
        return Optional.empty();
    }

    private Path resolveSafely(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ObjectStorageException("Object key is required");
        }
        Path target = storageRoot.resolve(objectKey).normalize();
        if (target.equals(storageRoot) || !target.startsWith(storageRoot)) {
            throw new ObjectStorageException("Object key escapes the storage root");
        }
        return target;
    }

    private void moveAtomicallyWhenSupported(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            log.warn("Could not remove temporary object-storage file {}", temporaryFile, exception);
        }
    }
}
