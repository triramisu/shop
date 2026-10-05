package com.shop.order.internal.checkout.orchestration;

public enum InventoryOrchestrationStatus {
    REQUESTED,
    PROCESSING,
    RESERVED,
    RETRY_REQUIRED,
    COMPENSATING,
    FAILED,
    COMPENSATION_REQUIRED,
    PAYMENT_PENDING,
    PAYMENT_CONFIRMING,
    PAYMENT_CONFIRMED,
    PAYMENT_RELEASING,
    PAYMENT_RELEASED,
    PAYMENT_RECOVERY_REQUIRED
}
