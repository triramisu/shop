package com.shop.order.internal.checkout.orchestration;

public enum InventoryOrchestrationStatus {
    REQUESTED,
    PROCESSING,
    RESERVED,
    RETRY_REQUIRED,
    COMPENSATING,
    FAILED,
    COMPENSATION_REQUIRED
}
