package com.shop.order.internal.checkout.idempotency;

public enum CheckoutIdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
