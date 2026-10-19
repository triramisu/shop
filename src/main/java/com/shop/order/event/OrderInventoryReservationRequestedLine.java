package com.shop.order.event;

import java.util.Objects;
import java.util.UUID;

public record OrderInventoryReservationRequestedLine(
        UUID reservationId, UUID orderItemId, UUID productVariantId, String sku, long quantity) {

    public OrderInventoryReservationRequestedLine {
        Objects.requireNonNull(reservationId, "reservation id is required");
        Objects.requireNonNull(orderItemId, "order item id is required");
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
