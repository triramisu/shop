package com.shop.inventory.internal.configuration;

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
@ConfigurationProperties(prefix = "app.inventory.reservation")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryReservationProperties {

    Duration maxDuration = Duration.ofMinutes(30);
    Duration transactionTimeout = Duration.ofSeconds(3);
    int maxAttempts = 3;
    Duration retryBackoff = Duration.ofMillis(25);

    @PostConstruct
    void validate() {
        if (maxDuration == null || maxDuration.isZero() || maxDuration.isNegative()) {
            throw new IllegalStateException("Inventory reservation max-duration must be positive");
        }
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalStateException("Inventory reservation max-attempts must be between 1 and 5");
        }
        if (transactionTimeout == null || transactionTimeout.toSeconds() < 1 || transactionTimeout.toSeconds() > 30) {
            throw new IllegalStateException("Inventory reservation transaction-timeout must be between 1s and 30s");
        }
        if (retryBackoff == null || retryBackoff.isNegative()) {
            throw new IllegalStateException("Inventory reservation retry-backoff must not be negative");
        }
    }
}
