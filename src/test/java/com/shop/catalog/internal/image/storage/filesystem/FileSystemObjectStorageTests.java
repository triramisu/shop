package com.shop.catalog.internal.image.storage.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemObjectStorageTests {

    @TempDir
    private Path temporaryDirectory;

    private FileSystemObjectStorage objectStorage;

    @BeforeEach
    void setUp() {
        CatalogImageProperties properties = new CatalogImageProperties();
        properties.getStorage().setFilesystemRoot(temporaryDirectory.resolve("objects"));
        objectStorage = new FileSystemObjectStorage(properties);
        objectStorage.initialize();
    }

    @Test
    void storesAndDeletesAnObjectWithoutLeavingTemporaryFiles() throws Exception {
        ObjectStorageUpload upload = new ObjectStorageUpload("catalog/products/id/image.png", "image/png", bytes());

        objectStorage.store(upload);

        Path storedObject = temporaryDirectory.resolve("objects/catalog/products/id/image.png");
        assertThat(storedObject).hasBinaryContent(bytes());
        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            assertThat(paths.filter(path -> path.toString().endsWith(".tmp")).toList())
                    .isEmpty();
        }

        objectStorage.delete(upload.objectKey());
        objectStorage.delete(upload.objectKey());
        assertThat(storedObject).doesNotExist();
    }

    @Test
    void refusesPathTraversalBlankKeysAndDuplicateObjects() {
        ObjectStorageUpload pathTraversal = new ObjectStorageUpload("../escape.png", "image/png", bytes());

        assertThatThrownBy(() -> objectStorage.store(pathTraversal)).isInstanceOf(ObjectStorageException.class);
        assertThatThrownBy(() -> objectStorage.delete(" ")).isInstanceOf(ObjectStorageException.class);

        ObjectStorageUpload upload = new ObjectStorageUpload("safe/image.png", "image/png", bytes());
        objectStorage.store(upload);
        assertThatThrownBy(() -> objectStorage.store(upload)).isInstanceOf(ObjectStorageException.class);
    }

    private byte[] bytes() {
        return new byte[] {1, 2, 3};
    }
}
