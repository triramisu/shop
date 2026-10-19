package com.shop.catalog.internal.image.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

class S3ObjectStorageTests {

    private static final String KEY = "catalog/products/product-id/image.png";
    private final S3Client client = mock(S3Client.class);
    private final S3ObjectStorageProperties properties = new S3ObjectStorageProperties();
    private S3Presigner presigner;
    private CircuitBreaker breaker;
    private S3ObjectStorage storage;

    @BeforeEach
    void setUp() {
        properties.setRegion("ap-southeast-1");
        properties.setBucket("shop-test-product-images");
        properties.setPresignedUrlTtl(Duration.ofMinutes(5));
        presigner = S3Presigner.builder()
                .region(Region.AP_SOUTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsSessionCredentials.create("test-access-key", "test-secret-key", "test-session-token")))
                .build();
        var configuration = new S3ObjectStorageConfiguration();
        breaker = configuration.catalogS3CircuitBreaker(configuration.catalogS3CircuitBreakerRegistry(properties));
        storage = new S3ObjectStorage(client, presigner, properties, breaker);
    }

    @AfterEach
    void closePresigner() {
        presigner.close();
    }

    @Test
    void writesValidatedBytesWithConditionalPutAndCorrectBucket() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        storage.store(new ObjectStorageUpload(KEY, "image/png", bytes));
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo(properties.getBucket());
        assertThat(request.getValue().key()).isEqualTo(KEY);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");
        try (var stream = body.getValue().contentStreamProvider().newStream()) {
            assertThat(stream.readAllBytes()).containsExactly(bytes);
        }
    }

    @Test
    void repeatedDeleteDoesNotRequestPermanentVersionDeletion() {
        storage.delete(KEY);
        storage.delete(KEY);
        var request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client, times(2)).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(properties.getBucket());
        assertThat(request.getValue().key()).isEqualTo(KEY);
        assertThat(request.getValue().versionId()).isNull();
    }

    @Test
    void signsBrowserGetWithoutAnyS3CallAndIncludesTemporaryCredential() {
        URI url = storage.createReadUrl(KEY).orElseThrow();
        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("shop-test-product-images.s3.ap-southeast-1.amazonaws.com");
        assertThat(url.getPath()).isEqualTo("/" + KEY);
        assertThat(url.getRawQuery())
                .contains("X-Amz-Expires=300")
                .contains("X-Amz-Security-Token=test-session-token")
                .contains("X-Amz-Signature=")
                .doesNotContain("test-secret-key");
        // ObjectStorage returns only a URI: a browser must not need extra signed headers.
        assertThat(Arrays.asList(url.getRawQuery().split("&"))).contains("X-Amz-SignedHeaders=host");
        verifyNoInteractions(client);
    }

    @Test
    void startupAndHealthCheckOnlyHeadTheConfiguredBucket() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        storage.initialize();
        assertThat(storage.health().getStatus()).isEqualTo(Status.UP);
        var request = ArgumentCaptor.forClass(HeadBucketRequest.class);
        verify(client, times(2)).headBucket(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(properties.getBucket());
    }

    @Test
    void missingBucketAndAccessDeniedFailClosedWithoutProviderDetails() {
        for (int status : new int[] {403, 404}) {
            when(client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .statusCode(status)
                            .message("sensitive-provider-body")
                            .build());
            assertThatThrownBy(storage::initialize)
                    .isInstanceOf(ObjectStorageException.class)
                    .hasNoCause()
                    .hasMessageNotContaining("sensitive-provider-body");
            assertThat(storage.health().getStatus()).isEqualTo(Status.DOWN);
            assertThat(storage.health().getDetails()).containsOnlyKeys("reason");
        }
    }

    @Test
    void duplicateAndAuthorizationFailuresAreNotRetriedOrCountedAsAvailabilityFailures() {
        for (int status : new int[] {400, 403, 409, 412}) {
            assertThat(S3ObjectStorageConfiguration.isAvailabilityFailure(
                            S3Exception.builder().statusCode(status).build()))
                    .isFalse();
        }
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder()
                        .statusCode(412)
                        .message("existing-object")
                        .build());
        assertThatThrownBy(() -> storage.store(new ObjectStorageUpload(KEY, "image/png", new byte[] {1})))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessageNotContaining("existing-object");
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        verify(client, times(0)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void transientFailuresOpenBreakerAndHalfOpenSuccessRecovers() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder()
                        .statusCode(503)
                        .message("sensitive-data")
                        .build());
        for (int count = 0; count < 5; count++) {
            assertThat(storage.health().getStatus()).isEqualTo(Status.DOWN);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(storage.health().getStatus()).isEqualTo(Status.DOWN);
        verify(client, times(5)).headBucket(any(HeadBucketRequest.class));
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        breaker.transitionToHalfOpenState();
        assertThat(storage.health().getStatus()).isEqualTo(Status.UP);
        assertThat(storage.health().getStatus()).isEqualTo(Status.UP);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(S3ObjectStorageConfiguration.isAvailabilityFailure(SdkClientException.create("timeout")))
                .isTrue();
        assertThat(S3ObjectStorageConfiguration.isAvailabilityFailure(
                        S3Exception.builder().statusCode(429).build()))
                .isTrue();
        assertThat(S3ObjectStorageConfiguration.isAvailabilityFailure(new IllegalArgumentException()))
                .isFalse();
    }

    @Test
    void rejectsInvalidKeysForAllOperationsBeforeAnyRemoteCall() {
        for (String key : new String[] {
            null,
            "",
            "catalog/products/",
            "other/file.png",
            KEY + " ",
            "catalog/products/../outside.png",
            KEY + "\n",
            KEY + "/",
            "catalog/products/" + "đ".repeat(512)
        }) {
            assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(ObjectStorageException.class);
            assertThatThrownBy(() -> storage.createReadUrl(key)).isInstanceOf(ObjectStorageException.class);
        }
        assertThatThrownBy(() -> storage.store(new ObjectStorageUpload("other/key", "image/png", new byte[] {1})))
                .isInstanceOf(ObjectStorageException.class);
        verifyNoInteractions(client);
        assertThatNoException()
                .isThrownBy(() ->
                        storage.createReadUrl("catalog/products/" + "x".repeat(1024 - "catalog/products/".length())));
    }

    @Test
    void signingFailureDoesNotLeakCredentialsOrFakeAReadUrl() {
        var failed = mock(S3Presigner.class);
        when(failed.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(SdkClientException.create("sensitive-session-token"));
        var unavailable = new S3ObjectStorage(client, failed, properties, breaker);
        assertThatThrownBy(() -> unavailable.createReadUrl(KEY))
                .isInstanceOf(ObjectStorageException.class)
                .hasNoCause()
                .hasMessageNotContaining("sensitive-session-token");
    }
}
