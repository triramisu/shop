package com.shop.order.internal.checkout.idempotency;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.order.checkout.idempotency")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutIdempotencyProperties {

    static final Duration MIN_RETENTION = Duration.ofHours(1);
    static final Duration MAX_RETENTION = Duration.ofDays(30);

    Duration retention = Duration.ofHours(24);
    Cleanup cleanup = new Cleanup();

    @PostConstruct
    void validate() {
        if (retention == null || retention.compareTo(MIN_RETENTION) < 0 || retention.compareTo(MAX_RETENTION) > 0) {
            throw new IllegalStateException("Checkout idempotency retention must be between 1h and 30d");
        }
        if (cleanup == null) {
            throw new IllegalStateException("Checkout idempotency cleanup configuration is required");
        }
        cleanup.validate();
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Cleanup {

        boolean enabled = true;
        Duration fixedDelay = Duration.ofHours(1);
        Duration initialDelay = Duration.ofHours(1);

        void validate() {
            if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
                throw new IllegalStateException("Checkout idempotency cleanup fixed-delay must be positive");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalStateException("Checkout idempotency cleanup initial-delay must not be negative");
            }
        }
    }
}
