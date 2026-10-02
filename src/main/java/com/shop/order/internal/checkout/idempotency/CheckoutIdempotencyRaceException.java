package com.shop.order.internal.checkout.idempotency;

final class CheckoutIdempotencyRaceException extends RuntimeException {

    CheckoutIdempotencyRaceException(Throwable cause) {
        super("another request created the idempotency scope concurrently", cause);
    }
}
