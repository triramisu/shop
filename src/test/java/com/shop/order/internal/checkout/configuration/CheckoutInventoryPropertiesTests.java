package com.shop.order.internal.checkout.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheckoutInventoryPropertiesTests {

    @Test
    void normalizesAValidLocationAndAcceptsTheInventoryTtlBoundary() {
        CheckoutInventoryProperties properties = new CheckoutInventoryProperties();
        properties.setLocationCode(" main ");
        properties.setReservationDuration(Duration.ofMinutes(30));

        properties.validate();

        assertThat(properties.getLocationCode()).isEqualTo("MAIN");
        assertThat(properties.getReservationDuration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void rejectsAnInvalidLocationOrReservationDuration() {
        CheckoutInventoryProperties properties = new CheckoutInventoryProperties();
        properties.setLocationCode(" ");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        properties.setLocationCode("MAIN");
        properties.setReservationDuration(Duration.ofMillis(999));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        properties.setReservationDuration(Duration.ofMinutes(31));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesReconciliationSafetyBoundaries() {
        CheckoutInventoryProperties properties = new CheckoutInventoryProperties();
        var reconciliation = properties.getReconciliation();
        reconciliation.setStaleAfter(Duration.ofSeconds(1));
        reconciliation.setFixedDelay(Duration.ofHours(1));
        reconciliation.setInitialDelay(Duration.ZERO);
        reconciliation.setBatchSize(1_000);

        properties.validate();

        reconciliation.setStaleAfter(Duration.ofMillis(999));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        reconciliation.setStaleAfter(Duration.ofSeconds(30));
        reconciliation.setFixedDelay(Duration.ofHours(1).plusSeconds(1));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        reconciliation.setFixedDelay(Duration.ofSeconds(30));
        reconciliation.setInitialDelay(Duration.ofMillis(-1));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        reconciliation.setInitialDelay(Duration.ZERO);
        reconciliation.setBatchSize(1_001);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setReconciliation(null);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
