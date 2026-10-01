package com.shop.order.internal.checkout.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderInventoryReservationPlan(
        UUID requestEventId,
        UUID correlationId,
        UUID orderId,
        Instant expiresAt,
        List<OrderInventoryReservationLinePlan> pendingLines) {

    public OrderInventoryReservationPlan {
        Objects.requireNonNull(requestEventId, "request event id is required");
        Objects.requireNonNull(correlationId, "correlation id is required");
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(expiresAt, "expiration is required");
        pendingLines = List.copyOf(Objects.requireNonNull(pendingLines, "pending lines are required"));
    }
}
