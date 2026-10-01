package com.shop.order.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderInventoryReservedEvent(
        UUID eventId,
        UUID requestEventId,
        UUID correlationId,
        UUID orderId,
        List<UUID> reservationIds,
        Instant occurredAt) {

    public OrderInventoryReservedEvent {
        Objects.requireNonNull(eventId, "event id is required");
        Objects.requireNonNull(requestEventId, "request event id is required");
        Objects.requireNonNull(correlationId, "correlation id is required");
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(occurredAt, "occurrence time is required");
        reservationIds = List.copyOf(Objects.requireNonNull(reservationIds, "reservation ids are required"));
        if (reservationIds.isEmpty()) {
            throw new IllegalArgumentException("reservation ids are required");
        }
        if (reservationIds.stream().distinct().count() != reservationIds.size()) {
            throw new IllegalArgumentException("reservation ids must be unique");
        }
    }
}
