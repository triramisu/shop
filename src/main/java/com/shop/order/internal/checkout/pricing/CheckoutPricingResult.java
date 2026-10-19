package com.shop.order.internal.checkout.pricing;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CheckoutPricingResult(
        UUID cartId, long cartVersion, Instant pricedAt, CheckoutPricingBreakdown breakdown) {

    public CheckoutPricingResult {
        Objects.requireNonNull(cartId, "cart id is required");
        if (cartVersion < 0) {
            throw new IllegalArgumentException("cart version must not be negative");
        }
        Objects.requireNonNull(pricedAt, "pricing time is required");
        Objects.requireNonNull(breakdown, "pricing breakdown is required");
    }
}
