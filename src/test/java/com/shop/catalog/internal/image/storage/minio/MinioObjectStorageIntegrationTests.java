package com.shop.catalog.internal.image.storage.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class MinioObjectStorageIntegrationTests {

    private static final String ACCESS_KEY = "integration-access";
    private static final String SECRET_KEY = "integration-secret-key";
    private static final DockerImageName SILO_IMAGE = DockerImageName.parse("pgsty/silo:RELEASE.2026-08-06T00-00-00Z");

    @Container
    private static final GenericContainer<?> minio = new GenericContainer<>(SILO_IMAGE)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    private CatalogImageProperties properties;
    private MinioObjectStorage objectStorage;

    @BeforeEach
    void setUp() {
        properties = minioProperties("shop-test-" + UUID.randomUUID());
        objectStorage = new MinioObjectStorage(client(), properties);
        objectStorage.initialize();
    }

    @Test
    void createsPrivateBucketStoresReadsAndDeletesObjects() throws Exception {
        byte[] content = {1, 2, 3, 4};
        ObjectStorageUpload upload = new ObjectStorageUpload("catalog/products/id/image.png", "image/png", content);

        objectStorage.store(upload);

        URI readUrl = objectStorage.createReadUrl(upload.objectKey()).orElseThrow();
        assertThat(readUrl.getRawQuery()).contains("X-Amz-Expires=300");
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(readUrl).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsExactly(content);

        URI unsignedUrl = URI.create(
                endpoint() + "/" + properties.getStorage().getMinio().getBucket() + "/" + upload.objectKey());
        HttpResponse<Void> unsignedResponse = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(unsignedUrl).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(unsignedResponse.statusCode()).isEqualTo(403);
        assertThat(objectStorage.health().getStatus()).isEqualTo(Status.UP);
        assertThatThrownBy(() -> objectStorage.store(upload)).isInstanceOf(ObjectStorageException.class);

        objectStorage.delete(upload.objectKey());
        objectStorage.delete(upload.objectKey());

        HttpResponse<Void> missingResponse = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(readUrl).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(missingResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void preservesObjectsAcrossAdapterRestartAndFailsWhenRequiredBucketIsMissing() throws Exception {
        ObjectStorageUpload upload = new ObjectStorageUpload("persistent/image.jpg", "image/jpeg", new byte[] {9, 8});
        objectStorage.store(upload);

        properties.getStorage().getMinio().setAutoCreateBucket(false);
        MinioObjectStorage restarted = new MinioObjectStorage(client(), properties);
        restarted.initialize();

        URI readUrl = restarted.createReadUrl(upload.objectKey()).orElseThrow();
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(readUrl).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.body()).containsExactly(9, 8);

        CatalogImageProperties missingBucket = minioProperties("missing-" + UUID.randomUUID());
        missingBucket.getStorage().getMinio().setAutoCreateBucket(false);
        MinioObjectStorage unavailable = new MinioObjectStorage(client(), missingBucket);
        assertThatThrownBy(unavailable::initialize).isInstanceOf(ObjectStorageException.class);
        assertThat(unavailable.health().getStatus()).isEqualTo(Status.DOWN);
        assertThatThrownBy(() -> restarted.delete(" ")).isInstanceOf(ObjectStorageException.class);
        assertThatThrownBy(() -> restarted.createReadUrl("x".repeat(1_025))).isInstanceOf(ObjectStorageException.class);
    }

    private CatalogImageProperties minioProperties(String bucket) {
        CatalogImageProperties catalogImageProperties = new CatalogImageProperties();
        catalogImageProperties.getStorage().setType(CatalogImageProperties.StorageType.MINIO);
        CatalogImageProperties.Minio minioProperties =
                catalogImageProperties.getStorage().getMinio();
        minioProperties.setEndpoint(URI.create(endpoint()));
        minioProperties.setAccessKey(ACCESS_KEY);
        minioProperties.setSecretKey(SECRET_KEY);
        minioProperties.setBucket(bucket);
        minioProperties.setAutoCreateBucket(true);
        minioProperties.setPresignedUrlTtl(Duration.ofMinutes(5));
        return catalogImageProperties;
    }

    private MinioClient client() {
        CatalogImageProperties.Minio minioProperties =
                properties == null ? null : properties.getStorage().getMinio();
        URI endpoint = minioProperties == null ? URI.create(endpoint()) : minioProperties.getEndpoint();
        return MinioClient.builder()
                .endpoint(endpoint.toString())
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
    }

    private String endpoint() {
        return "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
    }
}
