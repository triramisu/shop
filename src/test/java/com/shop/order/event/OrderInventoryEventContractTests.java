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
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant expiresAt = occurredAt.plusSeconds(60);
        List<OrderInventoryReservationRequestedLine> lines = List.of(first, duplicate);

        assertThatThrownBy(() -> new OrderInventoryReservationRequestedEvent(
                        eventId, correlationId, orderId, expiresAt, lines, occurredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void requiresCompensationSetsToBeDisjointAndConsistentWithStatus() {
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID requestEventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        List<UUID> released = List.of(reservationId);
        List<UUID> unresolved = List.of(reservationId);
        assertThatThrownBy(() -> new OrderInventoryCompensationEvent(
                        eventId,
                        requestEventId,
                        correlationId,
                        orderId,
                        OrderInventoryCompensationStatus.REQUIRED,
                        "FAILURE",
                        released,
                        unresolved,
                        occurredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disjoint");
        UUID completedEventId = UUID.randomUUID();
        List<UUID> completedUnresolved = List.of(UUID.randomUUID());
        assertThatThrownBy(() -> new OrderInventoryCompensationEvent(
                        completedEventId,
                        requestEventId,
                        correlationId,
                        orderId,
                        OrderInventoryCompensationStatus.COMPLETED,
                        "FAILURE",
                        List.of(),
                        completedUnresolved,
                        occurredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed");
    }

    private OrderInventoryReservationRequestedLine line(UUID reservationId, UUID orderItemId) {
        return new OrderInventoryReservationRequestedLine(reservationId, orderItemId, UUID.randomUUID(), "SKU-TEST", 1);
    }
}
