package com.shop.catalog.internal.image.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import com.shop.catalog.internal.image.storage.ObjectStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;

class S3ObjectStoragePropertiesTests {

    private S3ObjectStorageProperties valid() {
        var props = new S3ObjectStorageProperties();
        props.setRegion("ap-southeast-1");
        props.setBucket("shop-test-product-images");
        return props;
    }

    @Test
    void acceptsValidDefaultsAndBoundaryTtls() {
        var props = valid();
        assertThatNoException().isThrownBy(props::validate);
        props.setPresignedUrlTtl(Duration.ofSeconds(1));
        props.validate();
        props.setPresignedUrlTtl(Duration.ofHours(1));
        props.validate();
    }

    @Test
    void rejectsMissingOrUnsafeBucketAndRegion() {
        for (String region :
                new String[] {null, "", "https://evil.example", "ap-southeast-1/path", " ap-southeast-1"}) {
            invalid(p -> p.setRegion(region));
        }
        for (String bucket : new String[] {
            null,
            "",
            "ab",
            "A_B",
            "has.dot",
            "xn--reserved",
            "sthree-reserved",
            "bucket-s3alias",
            "bucket--ol-s3",
            "bucket--x-s3",
            "bucket--table-s3",
            "-bucket",
            "bucket-",
            "x".repeat(64)
        }) {
            invalid(p -> p.setBucket(bucket));
        }
    }

    @Test
    void rejectsInvalidTtlsTimeoutsAndCircuitBreakerLimits() {
        for (Duration duration : new Duration[] {null, Duration.ZERO, Duration.ofMillis(999), Duration.ofHours(2)}) {
            invalid(p -> p.setPresignedUrlTtl(duration));
        }
        invalid(p -> p.setConnectTimeout(null));
        invalid(p -> p.setConnectTimeout(Duration.ofSeconds(11)));
        invalid(p -> p.setReadTimeout(Duration.ofSeconds(31)));
        invalid(p -> p.setCallTimeout(Duration.ofSeconds(61)));
        invalid(p -> p.setCallTimeout(Duration.ofSeconds(1)));
        invalid(p -> p.setReadTimeout(Duration.ofSeconds(9)));
        invalid(p -> p.setCircuitBreaker(null));
        invalid(p -> p.getCircuitBreaker().setSlidingWindowSize(1));
        invalid(p -> p.getCircuitBreaker().setSlidingWindowSize(101));
        invalid(p -> p.getCircuitBreaker().setMinimumNumberOfCalls(0));
        invalid(p -> p.getCircuitBreaker().setMinimumNumberOfCalls(11));
        invalid(p -> p.getCircuitBreaker().setFailureRateThreshold(Float.NaN));
        invalid(p -> p.getCircuitBreaker().setFailureRateThreshold(0));
        invalid(p -> p.getCircuitBreaker().setFailureRateThreshold(101));
        invalid(p -> p.getCircuitBreaker().setOpenStateDuration(null));
        invalid(p -> p.getCircuitBreaker().setOpenStateDuration(Duration.ofMinutes(6)));
        invalid(p -> p.getCircuitBreaker().setPermittedCallsInHalfOpenState(0));
        invalid(p -> p.getCircuitBreaker().setPermittedCallsInHalfOpenState(11));
    }

    @Test
    void onlyBindsS3PropertiesWhenTheAdapterIsSelected() {
        new ApplicationContextRunner()
                .withUserConfiguration(S3ObjectStorageConfiguration.class)
                .withPropertyValues("app.catalog.images.storage.type=minio")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(S3Client.class);
                    assertThat(context).doesNotHaveBean(S3ObjectStorageProperties.class);
                });
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void productionS3WiresWithoutAnyMinioCredentialsAndRegistersMetrics() {
        var client = mock(S3Client.class);
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withBean("catalogS3Client", S3Client.class, () -> client)
                .withUserConfiguration(S3ObjectStorageConfiguration.class, CatalogImageProperties.class)
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "OBJECT_STORAGE_TYPE=s3",
                        "S3_REGION=ap-southeast-1",
                        "S3_BUCKET=shop-test-product-images")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ObjectStorage.class);
                    assertThat(context.getBean(CatalogImageProperties.class)
                                    .getStorage()
                                    .getType())
                            .isEqualTo(CatalogImageProperties.StorageType.S3);
                    assertThat(context.getBean(S3ObjectStorageProperties.class).getBucket())
                            .isEqualTo("shop-test-product-images");
                    var config = new S3ObjectStorageConfiguration();
                    var registry = config.catalogS3CircuitBreakerRegistry(valid());
                    config.catalogS3CircuitBreaker(registry);
                    var meters = new SimpleMeterRegistry();
                    try {
                        config.catalogS3CircuitBreakerMetrics(registry).bindTo(meters);
                        assertThat(meters.find("resilience4j.circuitbreaker.state")
                                        .tag("name", "catalogS3")
                                        .gauges())
                                .isNotEmpty();
                    } finally {
                        meters.close();
                    }
                });
    }

    @Test
    void productionClientHasFiniteTimeoutsAndNoImplicitRetries() {
        var config = new S3ObjectStorageConfiguration();
        try (var credentials = config.catalogS3CredentialsProvider();
                var client = config.catalogS3Client(valid(), credentials)) {
            var settings = client.serviceClientConfiguration().overrideConfiguration();
            assertThat(settings.apiCallTimeout()).contains(Duration.ofSeconds(8));
            assertThat(settings.apiCallAttemptTimeout()).contains(Duration.ofSeconds(8));
            assertThat(settings.retryStrategy().orElseThrow().maxAttempts()).isEqualTo(1);
        }
    }

    private void invalid(Consumer<S3ObjectStorageProperties> mutation) {
        var props = valid();
        mutation.accept(props);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(S3ObjectStorageProperties.class)
    static class PropertiesConfiguration {}
}
