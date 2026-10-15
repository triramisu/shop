package com.shop.order.internal.checkout.orchestration;

import com.shop.payment.event.PaymentStatus;
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
        UUID paymentAttemptId,
        Integer paymentAttemptNumber,
        PaymentStatus lastPaymentStatus,
        Instant lastPaymentEventAt,
        long version,
        List<OrderInventoryReservationLineSnapshot> lines) {

    public OrderInventoryOrchestrationSnapshot {
        lines = List.copyOf(lines);
    }
}
