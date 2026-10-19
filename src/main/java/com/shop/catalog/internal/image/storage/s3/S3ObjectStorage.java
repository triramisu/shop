package com.shop.catalog.internal.image.storage.s3;

import com.shop.catalog.internal.image.storage.ObjectStorage;
import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class S3ObjectStorage implements ObjectStorage, HealthIndicator {

    private static final String PREFIX = "catalog/products/";
    private final S3Client client;
    private final S3Presigner presigner;
    private final S3ObjectStorageProperties properties;
    private final CircuitBreaker breaker;

    S3ObjectStorage(
            S3Client client, S3Presigner presigner, S3ObjectStorageProperties properties, CircuitBreaker breaker) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
        this.breaker = breaker;
    }

    @PostConstruct
    void initialize() {
        execute(() -> client.headBucket(
                HeadBucketRequest.builder().bucket(properties.getBucket()).build()));
    }

    @Override
    public void store(ObjectStorageUpload upload) {
        String key = requireKey(upload.objectKey());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(upload.contentType())
                .ifNoneMatch("*")
                .build();
        execute(() -> client.putObject(request, RequestBody.fromBytes(upload.content())));
    }

    @Override
    public void delete(String objectKey) {
        String key = requireKey(objectKey);
        execute(() -> client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build()));
    }

    @Override
    public Optional<URI> createReadUrl(String objectKey) {
        String key = requireKey(objectKey);
        try {
            return Optional.of(presigner
                    .presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(properties.getPresignedUrlTtl())
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(properties.getBucket())
                                    .key(key)
                                    .build())
                            .build())
                    .url()
                    .toURI());
        } catch (Exception exception) {
            throw new ObjectStorageException("Không thể tạo URL đọc ảnh S3");
        }
    }

    @Override
    public Health health() {
        try {
            initialize();
            return Health.up().build();
        } catch (ObjectStorageException exception) {
            return Health.down().withDetail("reason", "s3-unavailable").build();
        }
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return breaker.executeSupplier(operation);
        } catch (RuntimeException exception) {
            // Provider errors and URLs may carry sensitive request details.
            throw new ObjectStorageException("Kho ảnh S3 hiện không khả dụng");
        }
    }

    private String requireKey(String key) {
        if (key == null
                || !key.startsWith(PREFIX)
                || key.length() <= PREFIX.length()
                || !key.equals(key.strip())
                || key.endsWith("/")
                || key.contains("..")
                || key.codePoints().anyMatch(Character::isISOControl)
                || key.getBytes(StandardCharsets.UTF_8).length > 1_024) {
            throw new ObjectStorageException("Object key ảnh S3 không hợp lệ");
        }
        return key;
    }
}
