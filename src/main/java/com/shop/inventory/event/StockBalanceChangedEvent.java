package com.shop.inventory.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockBalanceChangedEvent(
        UUID stockItemId,
        UUID productVariantId,
        String sku,
        String locationCode,
        long onHand,
        long reserved,
        long available,
        StockMovementType movementType,
        String referenceId,
        Instant occurredAt) {

    public StockBalanceChangedEvent {
        Objects.requireNonNull(stockItemId, "stock item id is required");
        Objects.requireNonNull(productVariantId, "product variant id is required");
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(locationCode, "location code is required");
        Objects.requireNonNull(movementType, "movement type is required");
        Objects.requireNonNull(occurredAt, "occurred at is required");
        if (onHand < 0 || reserved < 0 || reserved > onHand || available != onHand - reserved) {
            throw new IllegalArgumentException("stock event contains an invalid balance");
        }
    }
}
