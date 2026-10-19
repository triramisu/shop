package com.shop.catalog.internal.image.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

class CatalogImagePropertiesTests {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Test
    void acceptsValidFilesystemMemoryAndMinioConfigurations() {
        CatalogImageProperties filesystem = validProperties();
        CatalogImageProperties memory = validProperties();
        memory.getStorage().setType(CatalogImageProperties.StorageType.MEMORY);
        memory.getStorage().setFilesystemRoot(null);
        CatalogImageProperties minio = validMinioProperties();

        assertThatNoException().isThrownBy(filesystem::validate);
        assertThatNoException().isThrownBy(memory::validate);
        assertThatNoException().isThrownBy(minio::validate);
    }

    @Test
    void rejectsInvalidCountSizePixelAndStorageSettings() {
        CatalogImageProperties invalidCount = validProperties();
        invalidCount.setMaxFilesPerUpload(0);
        assertInvalid(invalidCount);

        CatalogImageProperties inconsistentCount = validProperties();
        inconsistentCount.setMaxFilesPerProduct(4);
        assertInvalid(inconsistentCount);

        CatalogImageProperties invalidSize = validProperties();
        invalidSize.setMaxFileSize(DataSize.ofBytes(0));
        assertInvalid(invalidSize);

        CatalogImageProperties excessiveSize = validProperties();
        excessiveSize.setMaxFileSize(DataSize.ofBytes(Integer.MAX_VALUE));
        assertInvalid(excessiveSize);

        CatalogImageProperties invalidPixels = validProperties();
        invalidPixels.setMaxPixels(0);
        assertInvalid(invalidPixels);

        CatalogImageProperties missingStorage = validProperties();
        missingStorage.setStorage(null);
        assertInvalid(missingStorage);

        CatalogImageProperties missingType = validProperties();
        missingType.getStorage().setType(null);
        assertInvalid(missingType);

        CatalogImageProperties missingRoot = validProperties();
        missingRoot.getStorage().setFilesystemRoot(null);
        assertInvalid(missingRoot);

        CatalogImageProperties missingMinio = validMinioProperties();
        missingMinio.getStorage().setMinio(null);
        assertInvalid(missingMinio);

        CatalogImageProperties invalidEndpoint = validMinioProperties();
        invalidEndpoint.getStorage().getMinio().setEndpoint(URI.create("ftp://localhost:9000/path"));
        assertInvalid(invalidEndpoint);

        CatalogImageProperties endpointWithCredentials = validMinioProperties();
        endpointWithCredentials.getStorage().getMinio().setEndpoint(URI.create("http://user@localhost:9000"));
        assertInvalid(endpointWithCredentials);

        CatalogImageProperties missingCredentials = validMinioProperties();
        missingCredentials.getStorage().getMinio().setSecretKey(" ");
        assertInvalid(missingCredentials);

        CatalogImageProperties invalidBucket = validMinioProperties();
        invalidBucket.getStorage().getMinio().setBucket("Invalid_Bucket");
        assertInvalid(invalidBucket);

        CatalogImageProperties invalidTtl = validMinioProperties();
        invalidTtl.getStorage().getMinio().setPresignedUrlTtl(Duration.ofDays(8));
        assertInvalid(invalidTtl);
    }

    @Test
    void failsApplicationContextWhenMinioCredentialsAreMissing() {
        contextRunner
                .withPropertyValues(
                        "app.catalog.images.storage.type=minio",
                        "app.catalog.images.storage.minio.endpoint=https://objects.example.com",
                        "app.catalog.images.storage.minio.bucket=shop-product-images")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Catalog image MinIO credentials are required");
                });
    }

    private CatalogImageProperties validProperties() {
        CatalogImageProperties properties = new CatalogImageProperties();
        properties.getStorage().setFilesystemRoot(Path.of("objects"));
        return properties;
    }

    private CatalogImageProperties validMinioProperties() {
        CatalogImageProperties properties = validProperties();
        properties.getStorage().setType(CatalogImageProperties.StorageType.MINIO);
        CatalogImageProperties.Minio minio = properties.getStorage().getMinio();
        minio.setEndpoint(URI.create("http://localhost:9000"));
        minio.setAccessKey("access-key");
        minio.setSecretKey("secret-key");
        minio.setBucket("shop-product-images");
        minio.setPresignedUrlTtl(Duration.ofMinutes(15));
        return properties;
    }

    private void assertInvalid(CatalogImageProperties properties) {
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CatalogImageProperties.class)
    static class TestConfiguration {}
}
