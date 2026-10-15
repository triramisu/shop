package com.shop.order.internal.checkout.configuration;

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
@ConfigurationProperties(prefix = "app.order.checkout.inventory")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutInventoryProperties {

    String locationCode = "MAIN";
    Duration reservationDuration = Duration.ofMinutes(15);
    Reconciliation reconciliation = new Reconciliation();

    @PostConstruct
    void validate() {
        if (locationCode == null || !locationCode.strip().matches("[A-Za-z0-9][A-Za-z0-9_-]{1,63}")) {
            throw new IllegalStateException("Checkout inventory location-code has an invalid format");
        }
        locationCode = locationCode.strip().toUpperCase(java.util.Locale.ROOT);
        if (reservationDuration == null
                || reservationDuration.compareTo(Duration.ofSeconds(1)) < 0
                || reservationDuration.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalStateException("Checkout inventory reservation-duration must be between 1s and 30m");
        }
        if (reconciliation == null) {
            throw new IllegalStateException("Checkout inventory reconciliation configuration is required");
        }
        reconciliation.validate();
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Reconciliation {

        boolean enabled = true;
        Duration staleAfter = Duration.ofSeconds(30);
        Duration fixedDelay = Duration.ofSeconds(30);
        Duration initialDelay = Duration.ofSeconds(30);
        int batchSize = 50;

        void validate() {
            if (staleAfter == null
                    || staleAfter.compareTo(Duration.ofSeconds(1)) < 0
                    || staleAfter.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException(
                        "Checkout inventory reconciliation stale-after must be between 1s and 1h");
            }
            if (fixedDelay == null
                    || fixedDelay.compareTo(Duration.ofSeconds(1)) < 0
                    || fixedDelay.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException(
                        "Checkout inventory reconciliation fixed-delay must be between 1s and 1h");
            }
            if (initialDelay == null || initialDelay.isNegative() || initialDelay.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException(
                        "Checkout inventory reconciliation initial-delay must be between 0s and 1h");
            }
            if (batchSize < 1 || batchSize > 1_000) {
                throw new IllegalStateException(
                        "Checkout inventory reconciliation batch-size must be between 1 and 1000");
            }
        }
    }
}
