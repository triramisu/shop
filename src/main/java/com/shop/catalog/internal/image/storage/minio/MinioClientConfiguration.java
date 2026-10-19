package com.shop.catalog.internal.image.storage.minio;

import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.catalog.images.storage", name = "type", havingValue = "minio")
public class MinioClientConfiguration {

    @Bean
    MinioClient minioClient(CatalogImageProperties properties) {
        CatalogImageProperties.Minio minio = properties.getStorage().getMinio();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint().toString())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
