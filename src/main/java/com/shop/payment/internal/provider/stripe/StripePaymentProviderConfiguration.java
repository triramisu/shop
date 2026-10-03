package com.shop.payment.internal.provider.stripe;

import com.shop.payment.provider.PaymentProviderException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "stripe")
@EnableConfigurationProperties(StripePaymentProviderProperties.class)
class StripePaymentProviderConfiguration {

    @Bean
    StripePaymentProviderPropertiesValidator stripePaymentProviderPropertiesValidator(
            StripePaymentProviderProperties properties) {
        return new StripePaymentProviderPropertiesValidator(properties);
    }

    @Bean
    StripeCheckoutStateSigner stripeCheckoutStateSigner(
            StripePaymentProviderProperties properties, StripePaymentProviderPropertiesValidator propertiesValidator) {
        return new StripeCheckoutStateSigner(
                properties.getReturnStateSecret(), properties.getReturnStateTtl(), Clock.systemUTC());
    }

    @Bean
    StripeCheckoutRequestFactory stripeCheckoutRequestFactory(
            StripePaymentProviderProperties properties, StripeCheckoutStateSigner stateSigner) {
        return new StripeCheckoutRequestFactory(properties.getSuccessUrl(), properties.getCancelUrl(), stateSigner);
    }

    @Bean
    OkHttpClient stripePaymentHttpClient(
            StripePaymentProviderProperties properties, StripePaymentProviderPropertiesValidator propertiesValidator) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(properties.getCallTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    @Bean
    StripeCheckoutGateway stripeCheckoutGateway(
            OkHttpClient stripePaymentHttpClient,
            ObjectMapper objectMapper,
            StripePaymentProviderProperties properties) {
        return new StripeCheckoutHttpGateway(
                stripePaymentHttpClient, objectMapper, properties.getSecretKey(), properties.getApiVersion());
    }

    @Bean
    CircuitBreakerRegistry stripePaymentCircuitBreakerRegistry(
            StripePaymentProviderProperties properties, StripePaymentProviderPropertiesValidator propertiesValidator) {
        StripePaymentProviderProperties.CircuitBreaker settings = properties.getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(settings.getSlidingWindowSize())
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
                .failureRateThreshold(settings.getFailureRateThreshold())
                .waitDurationInOpenState(settings.getOpenStateDuration())
                .permittedNumberOfCallsInHalfOpenState(settings.getPermittedCallsInHalfOpenState())
                .recordException(StripePaymentProviderConfiguration::isRetryableProviderFailure)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    CircuitBreaker stripePaymentCircuitBreaker(CircuitBreakerRegistry stripePaymentCircuitBreakerRegistry) {
        return stripePaymentCircuitBreakerRegistry.circuitBreaker("stripeCheckout");
    }

    @Bean
    RetryRegistry stripePaymentRetryRegistry(
            StripePaymentProviderProperties properties, StripePaymentProviderPropertiesValidator propertiesValidator) {
        StripePaymentProviderProperties.Retry settings = properties.getRetry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(settings.getMaxAttempts())
                .waitDuration(settings.getWaitDuration())
                .retryOnException(StripePaymentProviderConfiguration::isRetryableProviderFailure)
                .build();
        return RetryRegistry.of(config);
    }

    @Bean
    Retry stripePaymentRetry(RetryRegistry stripePaymentRetryRegistry) {
        return stripePaymentRetryRegistry.retry("stripeCheckout");
    }

    @Bean
    MeterBinder stripeCircuitBreakerMetrics(CircuitBreakerRegistry stripePaymentCircuitBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(stripePaymentCircuitBreakerRegistry);
    }

    @Bean
    MeterBinder stripeRetryMetrics(RetryRegistry stripePaymentRetryRegistry) {
        return TaggedRetryMetrics.ofRetryRegistry(stripePaymentRetryRegistry);
    }

    @Bean
    StripePaymentProvider stripePaymentProvider(
            StripeCheckoutGateway checkoutGateway,
            StripeCheckoutRequestFactory requestFactory,
            StripePaymentProviderProperties properties,
            CircuitBreaker stripePaymentCircuitBreaker,
            Retry stripePaymentRetry) {
        return new StripePaymentProvider(
                checkoutGateway,
                requestFactory,
                properties.getAllowedCheckoutHosts(),
                stripePaymentCircuitBreaker,
                stripePaymentRetry);
    }

    private static boolean isRetryableProviderFailure(Throwable exception) {
        return exception instanceof PaymentProviderException providerException && providerException.isRetryable();
    }
}
