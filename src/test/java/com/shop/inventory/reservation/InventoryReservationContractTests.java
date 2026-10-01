package com.shop.inventory.reservation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryReservationContractTests {

    @Test
    void rejectsNonPositiveReservationQuantity() {
        assertThatThrownBy(() -> new ReserveStockCommand(
                        UUID.randomUUID(), UUID.randomUUID(), 0, Instant.now().plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reservation quantity must be positive");
    }

    @Test
    void rejectsInconsistentResultBalances() {
        assertThatThrownBy(() -> new StockReservationResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        StockReservationStatus.RESERVED,
                        Instant.now().plusSeconds(60),
                        5,
                        2,
                        4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reservation result contains invalid quantities");
    }
}
