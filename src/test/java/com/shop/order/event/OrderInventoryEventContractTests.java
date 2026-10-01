package com.shop.order.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderInventoryEventContractTests {

    @Test
    void rejectsDuplicateReservationIdentityInARequest() {
        Instant occurredAt = Instant.parse("2026-10-02T00:00:00Z");
        UUID reservationId = UUID.randomUUID();
        var first = line(reservationId, UUID.randomUUID());
        var duplicate = line(reservationId, UUID.randomUUID());

        assertThatThrownBy(() -> new OrderInventoryReservationRequestedEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        occurredAt.plusSeconds(60),
                        List.of(first, duplicate),
                        occurredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void requiresCompensationSetsToBeDisjointAndConsistentWithStatus() {
        UUID reservationId = UUID.randomUUID();
        assertThatThrownBy(() -> new OrderInventoryCompensationEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OrderInventoryCompensationStatus.REQUIRED,
                        "FAILURE",
                        List.of(reservationId),
                        List.of(reservationId),
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disjoint");
        assertThatThrownBy(() -> new OrderInventoryCompensationEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OrderInventoryCompensationStatus.COMPLETED,
                        "FAILURE",
                        List.of(),
                        List.of(UUID.randomUUID()),
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed");
    }

    private OrderInventoryReservationRequestedLine line(UUID reservationId, UUID orderItemId) {
        return new OrderInventoryReservationRequestedLine(reservationId, orderItemId, UUID.randomUUID(), "SKU-TEST", 1);
    }
}
