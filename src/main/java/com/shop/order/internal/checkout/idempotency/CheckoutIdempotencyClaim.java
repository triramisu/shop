package com.shop.order.internal.checkout.idempotency;

import com.shop.shared.error.ErrorCode;
import java.util.Objects;
import java.util.UUID;

record CheckoutIdempotencyClaim(
        CheckoutIdempotencyClaimAction action, UUID executionId, UUID orderId, ErrorCode failure) {

    CheckoutIdempotencyClaim {
        Objects.requireNonNull(action, "claim action is required");
        if (action == CheckoutIdempotencyClaimAction.PROCEED && executionId == null) {
            throw new IllegalArgumentException("proceed claim requires an execution id");
        }
        if (action == CheckoutIdempotencyClaimAction.REPLAY && orderId == null) {
            throw new IllegalArgumentException("replay claim requires an order id");
        }
        if (action == CheckoutIdempotencyClaimAction.FAILED && failure == null) {
            throw new IllegalArgumentException("failed claim requires an error code");
        }
    }

    static CheckoutIdempotencyClaim proceed(UUID executionId) {
        return new CheckoutIdempotencyClaim(
                CheckoutIdempotencyClaimAction.PROCEED, Objects.requireNonNull(executionId), null, null);
    }

    static CheckoutIdempotencyClaim replay(UUID orderId) {
        return new CheckoutIdempotencyClaim(
                CheckoutIdempotencyClaimAction.REPLAY, null, Objects.requireNonNull(orderId), null);
    }

    static CheckoutIdempotencyClaim failed(ErrorCode failure) {
        return new CheckoutIdempotencyClaim(
                CheckoutIdempotencyClaimAction.FAILED, null, null, Objects.requireNonNull(failure));
    }

    static CheckoutIdempotencyClaim inProgress() {
        return new CheckoutIdempotencyClaim(CheckoutIdempotencyClaimAction.IN_PROGRESS, null, null, null);
    }
}
