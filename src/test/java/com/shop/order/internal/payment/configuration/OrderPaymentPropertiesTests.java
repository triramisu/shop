package com.shop.order.internal.payment.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OrderPaymentPropertiesTests {

    @Test
    void acceptsRecoverySafetyBoundaries() {
        OrderPaymentProperties properties = new OrderPaymentProperties();
        var recovery = properties.getRecovery();
        recovery.setStaleAfter(Duration.ofSeconds(1));
        recovery.setFixedDelay(Duration.ofHours(1));
        recovery.setInitialDelay(Duration.ZERO);
        recovery.setBatchSize(1_000);

        properties.validate();

        assertThat(properties.isAutoInitiationEnabled()).isTrue();
        assertThat(properties.isEventConsumptionEnabled()).isTrue();
    }

    @Test
    void rejectsUnsafeRecoveryConfiguration() {
        OrderPaymentProperties properties = new OrderPaymentProperties();
        var recovery = properties.getRecovery();

        recovery.setStaleAfter(Duration.ofMillis(999));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        recovery.setStaleAfter(Duration.ofSeconds(30));
        recovery.setFixedDelay(Duration.ofHours(1).plusSeconds(1));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        recovery.setFixedDelay(Duration.ofSeconds(30));
        recovery.setInitialDelay(Duration.ofMillis(-1));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        recovery.setInitialDelay(Duration.ZERO);
        recovery.setBatchSize(1_001);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setRecovery(null);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
