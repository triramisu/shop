package com.shop.payment.internal.provider.stripe;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.stripe")
class StripePaymentProviderProperties {

    private String secretKey;
    private String apiVersion = "2026-09-30.endive";
    private String returnStateSecret;
    private URI successUrl;
    private URI cancelUrl;
    private Set<String> allowedReturnHosts = new LinkedHashSet<>();
    private Set<String> allowedCheckoutHosts = new LinkedHashSet<>(Set.of("checkout.stripe.com"));
    private Duration returnStateTtl = Duration.ofHours(24);
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration callTimeout = Duration.ofSeconds(8);
    private Retry retry = new Retry();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    @Getter
    @Setter
    static class Retry {
        private int maxAttempts = 2;
        private Duration waitDuration = Duration.ofMillis(200);
    }

    @Getter
    @Setter
    static class CircuitBreaker {
        private int slidingWindowSize = 20;
        private int minimumNumberOfCalls = 10;
        private float failureRateThreshold = 50.0F;
        private Duration openStateDuration = Duration.ofSeconds(30);
        private int permittedCallsInHalfOpenState = 3;
    }
}
