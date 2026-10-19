package com.shop.order.internal.checkout.orchestration;

import java.util.Objects;
import java.util.UUID;

public record OrderInventoryReservationLinePlan(UUID reservationId, UUID productVariantId, String sku, long quantity) {

    public OrderInventoryReservationLinePlan {
        Objects.requireNonNull(reservationId, "reservation id is required");
        Objects.requireNonNull(productVariantId, "product variant id is required");
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        sku = sku.strip();
        if (quantity <= 0) {
            throw new IllegalArgumentException("reservation quantity must be positive");
        }
    }
}
