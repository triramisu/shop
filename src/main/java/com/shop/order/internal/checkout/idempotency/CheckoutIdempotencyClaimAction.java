package com.shop.order.internal.checkout.idempotency;

enum CheckoutIdempotencyClaimAction {
    PROCEED,
    REPLAY,
    FAILED,
    IN_PROGRESS
}
