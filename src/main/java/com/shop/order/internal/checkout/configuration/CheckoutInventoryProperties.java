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
    }
}
