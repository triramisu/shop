package com.shop.payment.internal.reconciliation;

import java.time.Duration;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.payment.reconciliation")
public class ReconciliationProperties {
    private boolean enabled;
    private int batchSize = 10;
    private int maxAttempts = 12;
    private Duration minimumAge = Duration.ofMinutes(10);
    private Duration maximumAge = Duration.ofHours(24);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration providerInterval = Duration.ofSeconds(5);
    private Duration retryBase = Duration.ofMinutes(1);
    private Duration retryMax = Duration.ofMinutes(30);
    private Duration fixedDelay = Duration.ofSeconds(10);
    private Duration initialDelay = Duration.ofSeconds(30);

    void validate() {
        if (batchSize < 1
                || batchSize > 20
                || maxAttempts < 1
                || maxAttempts > 20
                || !between(minimumAge, Duration.ofMinutes(1), Duration.ofHours(1))
                || !between(maximumAge, Duration.ofHours(1), Duration.ofDays(7))
                || !between(leaseDuration, Duration.ofSeconds(20), Duration.ofMinutes(2))
                || !between(providerInterval, Duration.ofSeconds(5), Duration.ofMinutes(1))
                || !between(retryBase, Duration.ofSeconds(30), Duration.ofMinutes(10))
                || !between(retryMax, retryBase, Duration.ofHours(6))
                || !between(fixedDelay, Duration.ofSeconds(5), Duration.ofMinutes(5))
                || !between(initialDelay, Duration.ofSeconds(1), Duration.ofMinutes(5))) {
            throw new IllegalStateException("Payment reconciliation settings are invalid");
        }
    }

    Duration backoff(UUID id, int count) {
        long millis =
                Math.min(retryMax.toMillis(), retryBase.toMillis() * (1L << Math.min(20, Math.max(0, count - 1))));
        long jitter = Math.floorMod(id.hashCode() + count, Math.max(1, millis / 10 + 1));
        return Duration.ofMillis(Math.min(retryMax.toMillis(), millis + jitter));
    }

    private static boolean between(Duration value, Duration min, Duration max) {
        return value != null && min != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
