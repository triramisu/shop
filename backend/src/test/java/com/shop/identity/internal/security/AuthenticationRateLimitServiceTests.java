package com.shop.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.identity.internal.configuration.AuthenticationRateLimitProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationRateLimitServiceTests {

    private AuthenticationRateLimitProperties properties;
    private AuthenticationRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        properties = new AuthenticationRateLimitProperties();
        properties.setLogin(AuthenticationRateLimitProperties.Limit.builder()
                .capacity(2)
                .refillPeriod(Duration.ofMinutes(1))
                .build());
        rateLimitService = new AuthenticationRateLimitService(properties);
    }

    @Test
    void allowsTheConfiguredBurstAndThenRejectsTheClient() {
        assertThat(rateLimitService.tryConsume("/api/auth/token", "192.0.2.1").allowed())
                .isTrue();
        assertThat(rateLimitService.tryConsume("/api/auth/token", "192.0.2.1").allowed())
                .isTrue();

        AuthenticationRateLimitService.RateLimitDecision rejected =
                rateLimitService.tryConsume("/api/auth/token", "192.0.2.1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isPositive();
    }

    @Test
    void bypassesTheLimiterWhenItIsDisabled() {
        properties.setEnabled(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(rateLimitService
                            .tryConsume("/api/auth/token", "192.0.2.2")
                            .allowed())
                    .isTrue();
        }
    }

    @Test
    void failsClosedWhenTheBucketMemoryLimitIsReached() {
        properties.setMaxBuckets(1);

        assertThat(rateLimitService.tryConsume("/api/auth/token", "192.0.2.3").allowed())
                .isTrue();

        AuthenticationRateLimitService.RateLimitDecision rejected =
                rateLimitService.tryConsume("/api/auth/token", "192.0.2.4");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void ignoresEndpointsOutsideTheAuthenticationPolicy() {
        assertThat(rateLimitService.tryConsume("/actuator/health", "192.0.2.5").allowed())
                .isTrue();
    }
}
