package com.shop.catalog.internal.image.configuration;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
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

    private static final Duration MAX_PRESIGNED_URL_TTL = Duration.ofDays(7);
    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");

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
        if (storage.type == StorageType.MINIO) {
            validateMinio(storage.minio);
        }
    }

    private void validateMinio(Minio minio) {
        if (minio == null) {
            throw new IllegalStateException("Catalog image MinIO configuration is required");
        }
        URI endpoint = minio.endpoint;
        if (endpoint == null
                || !endpoint.isAbsolute()
                || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null
                || (endpoint.getPath() != null && !endpoint.getPath().isBlank() && !"/".equals(endpoint.getPath()))) {
            throw new IllegalStateException("Catalog image MinIO endpoint is invalid");
        }
        if (isBlank(minio.accessKey) || isBlank(minio.secretKey)) {
            throw new IllegalStateException("Catalog image MinIO credentials are required");
        }
        if (isBlank(minio.bucket)
                || !BUCKET_NAME_PATTERN.matcher(minio.bucket).matches()
                || minio.bucket.contains("..")) {
            throw new IllegalStateException("Catalog image MinIO bucket is invalid");
        }
        if (minio.presignedUrlTtl == null
                || minio.presignedUrlTtl.isZero()
                || minio.presignedUrlTtl.isNegative()
                || minio.presignedUrlTtl.compareTo(MAX_PRESIGNED_URL_TTL) > 0) {
            throw new IllegalStateException("Catalog image MinIO presigned URL TTL is invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum StorageType {
        FILESYSTEM,
        MINIO,
        MEMORY
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Storage {
        StorageType type = StorageType.FILESYSTEM;
        Path filesystemRoot;
        Minio minio = new Minio();
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Minio {
        URI endpoint;
        String accessKey;
        String secretKey;
        String bucket = "shop-product-images";
        boolean autoCreateBucket;
        Duration presignedUrlTtl = Duration.ofMinutes(15);
    }
}
