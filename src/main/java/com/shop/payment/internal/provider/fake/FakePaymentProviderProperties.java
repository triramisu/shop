package com.shop.payment.internal.provider.fake;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.fake")
class FakePaymentProviderProperties {

    private FakePaymentMode defaultMode = FakePaymentMode.SUCCESS;
    private int maxStoredAttempts = 10_000;
    private int maxCallbackDeliveries = 10;

    @PostConstruct
    void validate() {
        if (defaultMode == null) {
            throw new IllegalStateException("Fake payment default mode is required");
        }
        if (maxStoredAttempts < 1 || maxStoredAttempts > 100_000) {
            throw new IllegalStateException("Fake payment max stored attempts must be between 1 and 100000");
        }
        if (maxCallbackDeliveries < 1 || maxCallbackDeliveries > 100) {
            throw new IllegalStateException("Fake payment max callback deliveries must be between 1 and 100");
        }
    }
}
