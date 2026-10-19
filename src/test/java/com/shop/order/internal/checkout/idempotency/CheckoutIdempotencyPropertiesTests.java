package com.shop.order.internal.checkout.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheckoutIdempotencyPropertiesTests {

    @Test
    void acceptsSafeDefaults() {
        CheckoutIdempotencyProperties properties = new CheckoutIdempotencyProperties();

        properties.validate();

        assertThat(properties.getRetention()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.getCleanup().isEnabled()).isTrue();
        assertThat(properties.getCleanup().getFixedDelay()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void rejectsUnsafeRetentionAndCleanupIntervals() {
        CheckoutIdempotencyProperties shortRetention = new CheckoutIdempotencyProperties();
        shortRetention.setRetention(Duration.ofMinutes(59));
        assertThatThrownBy(shortRetention::validate).isInstanceOf(IllegalStateException.class);

        CheckoutIdempotencyProperties longRetention = new CheckoutIdempotencyProperties();
        longRetention.setRetention(Duration.ofDays(31));
        assertThatThrownBy(longRetention::validate).isInstanceOf(IllegalStateException.class);

        CheckoutIdempotencyProperties invalidCleanup = new CheckoutIdempotencyProperties();
        invalidCleanup.getCleanup().setFixedDelay(Duration.ZERO);
        assertThatThrownBy(invalidCleanup::validate).isInstanceOf(IllegalStateException.class);
    }
}
