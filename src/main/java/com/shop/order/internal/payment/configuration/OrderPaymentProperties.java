package com.shop.order.internal.payment.configuration;

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
@ConfigurationProperties(prefix = "app.order.payment")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderPaymentProperties {

    boolean autoInitiationEnabled = true;
    boolean eventConsumptionEnabled = true;
    Recovery recovery = new Recovery();

    @PostConstruct
    void validate() {
        if (recovery == null) {
            throw new IllegalStateException("Order payment recovery configuration is required");
        }
        recovery.validate();
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Recovery {

        boolean enabled = true;
        Duration staleAfter = Duration.ofSeconds(30);
        Duration fixedDelay = Duration.ofSeconds(30);
        Duration initialDelay = Duration.ofSeconds(30);
        int batchSize = 50;

        void validate() {
            if (staleAfter == null
                    || staleAfter.compareTo(Duration.ofSeconds(1)) < 0
                    || staleAfter.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException("Order payment recovery stale-after must be between 1s and 1h");
            }
            if (fixedDelay == null
                    || fixedDelay.compareTo(Duration.ofSeconds(1)) < 0
                    || fixedDelay.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException("Order payment recovery fixed-delay must be between 1s and 1h");
            }
            if (initialDelay == null || initialDelay.isNegative() || initialDelay.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException("Order payment recovery initial-delay must be between 0s and 1h");
            }
            if (batchSize < 1 || batchSize > 1_000) {
                throw new IllegalStateException("Order payment recovery batch-size must be between 1 and 1000");
            }
        }
    }
}
