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
    Expiration expiration = new Expiration();

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
        if (expiration == null) {
            throw new IllegalStateException("Inventory reservation expiration configuration is required");
        }
        expiration.validate();
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Expiration {

        boolean enabled = true;
        Duration fixedDelay = Duration.ofSeconds(30);
        Duration initialDelay = Duration.ofSeconds(30);
        int batchSize = 100;

        void validate() {
            if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
                throw new IllegalStateException("Inventory expiration fixed-delay must be positive");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalStateException("Inventory expiration initial-delay must not be negative");
            }
            if (batchSize < 1 || batchSize > 1_000) {
                throw new IllegalStateException("Inventory expiration batch-size must be between 1 and 1000");
            }
        }
    }
}
