package com.shop.catalog.internal.image.configuration;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Data
@Component
@ConfigurationProperties(prefix = "app.catalog.images")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CatalogImageProperties {

    int maxFilesPerUpload = 5;
    int maxFilesPerProduct = 10;
    DataSize maxFileSize = DataSize.ofMegabytes(5);
    long maxPixels = 40_000_000;
    Storage storage = new Storage();

    @PostConstruct
    void validate() {
        if (maxFilesPerUpload <= 0 || maxFilesPerProduct < maxFilesPerUpload) {
            throw new IllegalStateException("Catalog image count limits are invalid");
        }
        if (maxFileSize == null
                || maxFileSize.isNegative()
                || maxFileSize.toBytes() == 0
                || maxFileSize.toBytes() >= Integer.MAX_VALUE) {
            throw new IllegalStateException("Catalog image size limit must be positive");
        }
        if (maxPixels <= 0) {
            throw new IllegalStateException("Catalog image pixel limit must be positive");
        }
        if (storage == null || storage.type == null) {
            throw new IllegalStateException("Catalog image storage type is required");
        }
        if (storage.type == StorageType.FILESYSTEM && storage.filesystemRoot == null) {
            throw new IllegalStateException("Catalog image filesystem root is required");
        }
    }

    public enum StorageType {
        FILESYSTEM,
        MEMORY
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Storage {
        StorageType type = StorageType.FILESYSTEM;
        Path filesystemRoot;
    }
}
