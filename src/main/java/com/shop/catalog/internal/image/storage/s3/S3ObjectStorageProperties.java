package com.shop.catalog.internal.image.storage.s3;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.catalog.images.storage.s3")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class S3ObjectStorageProperties {

    private static final Pattern REGION = Pattern.compile("[a-z]{2}(?:-[a-z0-9]+)+-[1-9][0-9]*");
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]");

    String region;
    String bucket;
    Duration presignedUrlTtl = Duration.ofMinutes(15);
    Duration connectTimeout = Duration.ofSeconds(2);
    Duration readTimeout = Duration.ofSeconds(5);
    Duration callTimeout = Duration.ofSeconds(8);
    CircuitBreakerSettings circuitBreaker = new CircuitBreakerSettings();

    @PostConstruct
    void validate() {
        if (region == null || !REGION.matcher(region).matches()) {
            throw new IllegalStateException("Region S3 không hợp lệ");
        }
        if (bucket == null
                || !BUCKET.matcher(bucket).matches()
                || bucket.startsWith("xn--")
                || bucket.startsWith("sthree-")
                || bucket.endsWith("-s3alias")
                || bucket.endsWith("--ol-s3")
                || bucket.endsWith("--x-s3")
                || bucket.endsWith("--table-s3")) {
            throw new IllegalStateException("Tên bucket S3 ảnh sản phẩm không hợp lệ");
        }
        if (!within(presignedUrlTtl, Duration.ofHours(1))) {
            throw new IllegalStateException("Thời hạn URL S3 phải từ 1 giây đến 1 giờ");
        }
        if (!within(connectTimeout, Duration.ofSeconds(10))
                || !within(readTimeout, Duration.ofSeconds(30))
                || !within(callTimeout, Duration.ofSeconds(60))
                || callTimeout.compareTo(connectTimeout) < 0
                || callTimeout.compareTo(readTimeout) < 0) {
            throw new IllegalStateException("Timeout S3 không hợp lệ");
        }
        if (circuitBreaker == null
                || circuitBreaker.slidingWindowSize < 2
                || circuitBreaker.slidingWindowSize > 100
                || circuitBreaker.minimumNumberOfCalls < 1
                || circuitBreaker.minimumNumberOfCalls > circuitBreaker.slidingWindowSize
                || !Float.isFinite(circuitBreaker.failureRateThreshold)
                || circuitBreaker.failureRateThreshold < 1
                || circuitBreaker.failureRateThreshold > 100
                || !within(circuitBreaker.openStateDuration, Duration.ofMinutes(5))
                || circuitBreaker.permittedCallsInHalfOpenState < 1
                || circuitBreaker.permittedCallsInHalfOpenState > 10) {
            throw new IllegalStateException("Circuit breaker S3 không hợp lệ");
        }
    }

    private boolean within(Duration value, Duration maximum) {
        return value != null && value.compareTo(Duration.ofSeconds(1)) >= 0 && value.compareTo(maximum) <= 0;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CircuitBreakerSettings {
        int slidingWindowSize = 10;
        int minimumNumberOfCalls = 5;
        float failureRateThreshold = 50;
        Duration openStateDuration = Duration.ofSeconds(30);
        int permittedCallsInHalfOpenState = 2;
    }
}
