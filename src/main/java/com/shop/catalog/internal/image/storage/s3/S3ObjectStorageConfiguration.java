package com.shop.catalog.internal.image.storage.s3;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.catalog.images.storage", name = "type", havingValue = "s3")
@EnableConfigurationProperties(S3ObjectStorageProperties.class)
public class S3ObjectStorageConfiguration {

    @Bean(destroyMethod = "close")
    DefaultCredentialsProvider catalogS3CredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "catalogS3Client")
    S3Client catalogS3Client(
            S3ObjectStorageProperties properties,
            @Qualifier("catalogS3CredentialsProvider") DefaultCredentialsProvider credentials) {
        return clientBuilder(properties, credentials).build();
    }

    static S3ClientBuilder clientBuilder(S3ObjectStorageProperties properties, AwsCredentialsProvider credentials) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(properties.getConnectTimeout())
                        .socketTimeout(properties.getReadTimeout()))
                .overrideConfiguration(config -> config.apiCallTimeout(properties.getCallTimeout())
                        .apiCallAttemptTimeout(properties.getCallTimeout())
                        .retryStrategy(retry -> retry.maxAttempts(1)));
    }

    @Bean(destroyMethod = "close")
    S3Presigner catalogS3Presigner(
            S3ObjectStorageProperties properties,
            @Qualifier("catalogS3CredentialsProvider") DefaultCredentialsProvider credentials) {
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials)
                .build();
    }

    @Bean
    CircuitBreakerRegistry catalogS3CircuitBreakerRegistry(S3ObjectStorageProperties properties) {
        var settings = properties.getCircuitBreaker();
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(settings.getSlidingWindowSize())
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
                .failureRateThreshold(settings.getFailureRateThreshold())
                .waitDurationInOpenState(settings.getOpenStateDuration())
                .permittedNumberOfCallsInHalfOpenState(settings.getPermittedCallsInHalfOpenState())
                .recordException(S3ObjectStorageConfiguration::isAvailabilityFailure)
                .build());
    }

    @Bean
    CircuitBreaker catalogS3CircuitBreaker(
            @Qualifier("catalogS3CircuitBreakerRegistry") CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("catalogS3");
    }

    @Bean
    MeterBinder catalogS3CircuitBreakerMetrics(
            @Qualifier("catalogS3CircuitBreakerRegistry") CircuitBreakerRegistry registry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
    }

    @Bean
    S3ObjectStorage s3ObjectStorage(
            S3Client catalogS3Client,
            S3Presigner catalogS3Presigner,
            S3ObjectStorageProperties properties,
            @Qualifier("catalogS3CircuitBreaker") CircuitBreaker breaker) {
        return new S3ObjectStorage(catalogS3Client, catalogS3Presigner, properties, breaker);
    }

    static boolean isAvailabilityFailure(Throwable exception) {
        return exception instanceof SdkClientException
                || (exception instanceof S3Exception s3 && (s3.statusCode() == 429 || s3.statusCode() >= 500));
    }
}
