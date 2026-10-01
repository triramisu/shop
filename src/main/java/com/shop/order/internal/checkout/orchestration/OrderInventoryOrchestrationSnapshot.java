package com.shop.order.internal.checkout.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderInventoryOrchestrationSnapshot(
        UUID id,
        UUID orderId,
        UUID requestEventId,
        UUID correlationId,
        InventoryOrchestrationStatus status,
        Instant expiresAt,
        String failureCode,
        long version,
        List<OrderInventoryReservationLineSnapshot> lines) {

    public OrderInventoryOrchestrationSnapshot {
        lines = List.copyOf(lines);
    }
}
