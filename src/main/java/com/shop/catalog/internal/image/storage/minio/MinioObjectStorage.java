package com.shop.catalog.internal.image.storage.minio;

import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import com.shop.catalog.internal.image.storage.ObjectStorage;
import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.catalog.images.storage", name = "type", havingValue = "minio")
@Slf4j
public class MinioObjectStorage implements ObjectStorage, HealthIndicator {

    private static final int MAX_OBJECT_KEY_BYTES = 1_024;

    private final MinioClient client;
    private final CatalogImageProperties.Minio properties;

    public MinioObjectStorage(MinioClient client, CatalogImageProperties catalogImageProperties) {
        this.client = client;
        this.properties = catalogImageProperties.getStorage().getMinio();
    }

    @PostConstruct
    void initialize() {
        try {
            if (bucketExists()) {
                return;
            }
            if (!properties.isAutoCreateBucket()) {
                throw new ObjectStorageException("MinIO bucket does not exist and automatic creation is disabled");
            }
            client.makeBucket(
                    MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            log.info("Created private catalog image bucket {}", properties.getBucket());
        } catch (ObjectStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ObjectStorageException("Cannot initialize MinIO object storage", exception);
        }
    }

    @Override
    public void store(ObjectStorageUpload upload) {
        String objectKey = requireObjectKey(upload.objectKey());
        byte[] content = upload.content();
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).stream(
                            input, (long) content.length, -1L)
                    .contentType(upload.contentType())
                    .headers(Map.of("If-None-Match", "*"))
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("Cannot store MinIO object " + objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        String requiredObjectKey = requireObjectKey(objectKey);
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(requiredObjectKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("Cannot delete MinIO object " + requiredObjectKey, exception);
        }
    }

    @Override
    public Optional<URI> createReadUrl(String objectKey) {
        String requiredObjectKey = requireObjectKey(objectKey);
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(requiredObjectKey)
                    .expiry(Math.toIntExact(properties.getPresignedUrlTtl().toSeconds()))
                    .build());
            return Optional.of(URI.create(url));
        } catch (Exception exception) {
            throw new ObjectStorageException("Cannot create MinIO read URL", exception);
        }
    }

    @Override
    public Health health() {
        try {
            if (bucketExists()) {
                return Health.up().withDetail("bucket", properties.getBucket()).build();
            }
            return Health.down().withDetail("reason", "bucket-not-found").build();
        } catch (Exception exception) {
            log.warn("MinIO health check failed", exception);
            return Health.down().withDetail("reason", "unavailable").build();
        }
    }

    private boolean bucketExists() throws Exception {
        return client.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getBucket()).build());
    }

    private String requireObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ObjectStorageException("Object key is required");
        }
        String requiredObjectKey = objectKey.strip();
        if (requiredObjectKey.getBytes(StandardCharsets.UTF_8).length > MAX_OBJECT_KEY_BYTES) {
            throw new ObjectStorageException("Object key is too long");
        }
        return requiredObjectKey;
    }
}
