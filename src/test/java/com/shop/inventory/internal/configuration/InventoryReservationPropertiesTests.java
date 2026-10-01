package com.shop.inventory.internal.configuration;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InventoryReservationPropertiesTests {

    @Test
    void acceptsDefaultExpirationConfiguration() {
        InventoryReservationProperties properties = new InventoryReservationProperties();

        assertThatNoException().isThrownBy(properties::validate);
    }

    @Test
    void rejectsMissingOrInvalidExpirationConfiguration() {
        InventoryReservationProperties missing = new InventoryReservationProperties();
        missing.setExpiration(null);

        InventoryReservationProperties invalidDelay = new InventoryReservationProperties();
        invalidDelay.getExpiration().setFixedDelay(Duration.ZERO);

        InventoryReservationProperties invalidInitialDelay = new InventoryReservationProperties();
        invalidInitialDelay.getExpiration().setInitialDelay(Duration.ofSeconds(-1));

        InventoryReservationProperties invalidBatchSize = new InventoryReservationProperties();
        invalidBatchSize.getExpiration().setBatchSize(1_001);

        assertThatThrownBy(missing::validate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(invalidDelay::validate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(invalidInitialDelay::validate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(invalidBatchSize::validate).isInstanceOf(IllegalStateException.class);
    }
}
