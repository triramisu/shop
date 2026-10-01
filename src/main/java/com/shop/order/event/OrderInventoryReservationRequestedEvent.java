package com.shop.order.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderInventoryReservationRequestedEvent(
        UUID eventId,
        UUID correlationId,
        UUID orderId,
        Instant expiresAt,
        List<OrderInventoryReservationRequestedLine> lines,
        Instant occurredAt) {

    public OrderInventoryReservationRequestedEvent {
        Objects.requireNonNull(eventId, "event id is required");
        Objects.requireNonNull(correlationId, "correlation id is required");
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(expiresAt, "expiration is required");
        Objects.requireNonNull(occurredAt, "occurrence time is required");
        lines = List.copyOf(Objects.requireNonNull(lines, "reservation lines are required"));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("reservation lines are required");
        }
        if (lines.stream()
                                .map(OrderInventoryReservationRequestedLine::reservationId)
                                .distinct()
                                .count()
                        != lines.size()
                || lines.stream()
                                .map(OrderInventoryReservationRequestedLine::orderItemId)
                                .distinct()
                                .count()
                        != lines.size()) {
            throw new IllegalArgumentException("reservation and order item ids must be unique");
        }
        if (!expiresAt.isAfter(occurredAt)) {
            throw new IllegalArgumentException("reservation expiration must be in the future");
        }
    }
}
