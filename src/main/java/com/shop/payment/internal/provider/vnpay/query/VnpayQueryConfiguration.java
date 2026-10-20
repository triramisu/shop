package com.shop.payment.internal.provider.vnpay.query;

import com.shop.payment.internal.provider.vnpay.VnpayProperties;
import com.shop.payment.internal.provider.vnpay.VnpaySignature;
import com.shop.payment.provider.PaymentProviderException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "vnpay")
@ConditionalOnProperty(prefix = "app.payment.vnpay.query", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(VnpayQueryProperties.class)
class VnpayQueryConfiguration {
    @Bean
    OkHttpClient vnpayQueryHttpClient(VnpayQueryProperties properties) {
        VnpayQueryProperties.validate(properties);
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(properties.getCallTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    @Bean
    VnpayQueryGateway vnpayQueryGateway(
            @Qualifier("vnpayQueryHttpClient") OkHttpClient client,
            VnpayProperties merchant,
            VnpayQueryProperties properties,
            VnpaySignature signature,
            ObjectMapper mapper) {
        return new VnpayQueryHttpGateway(
                client,
                new VnpayQueryCodec(
                        merchant.getTmnCode(), properties.getServerIp(), signature, Clock.systemUTC(), mapper));
    }

    @Bean
    CircuitBreakerRegistry vnpayQueryCircuitBreakerRegistry() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .ignoreException(error -> error instanceof PaymentProviderException failure && !failure.isRetryable())
                .build());
    }

    @Bean
    MeterBinder vnpayQueryCircuitBreakerMetrics(
            @Qualifier("vnpayQueryCircuitBreakerRegistry") CircuitBreakerRegistry registry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
    }

    @Bean
    VnpayStatusQueryProvider vnpayStatusQueryProvider(
            VnpayQueryGateway gateway, @Qualifier("vnpayQueryCircuitBreakerRegistry") CircuitBreakerRegistry registry) {
        return new VnpayStatusQueryProvider(gateway, registry.circuitBreaker("vnpayQuery"));
    }
}
