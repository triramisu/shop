package com.shop.order.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderStatusChangedEvent(
        UUID eventId,
        UUID orderId,
        OrderStatus previousStatus,
        OrderStatus currentStatus,
        OrderTransitionEvent transitionEvent,
        OrderTransitionActor actor,
        Instant occurredAt) {

    public OrderStatusChangedEvent {
        Objects.requireNonNull(eventId, "event id is required");
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(previousStatus, "previous status is required");
        Objects.requireNonNull(currentStatus, "current status is required");
        Objects.requireNonNull(transitionEvent, "transition event is required");
        Objects.requireNonNull(actor, "transition actor is required");
        Objects.requireNonNull(occurredAt, "occurrence time is required");
        if (previousStatus == currentStatus) {
            throw new IllegalArgumentException("order status must change");
        }
    }
}
