package com.shop.order.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderInventoryReservationFailedEvent(
        UUID eventId,
        UUID requestEventId,
        UUID correlationId,
        UUID orderId,
        String failureCode,
        boolean retryable,
        Instant occurredAt) {

    public OrderInventoryReservationFailedEvent {
        Objects.requireNonNull(eventId, "event id is required");
        Objects.requireNonNull(requestEventId, "request event id is required");
        Objects.requireNonNull(correlationId, "correlation id is required");
        Objects.requireNonNull(orderId, "order id is required");
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failure code is required");
        }
        failureCode = failureCode.strip();
        Objects.requireNonNull(occurredAt, "occurrence time is required");
    }
}
