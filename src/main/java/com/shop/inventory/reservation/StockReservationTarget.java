package com.shop.inventory.reservation;

import java.util.Objects;
import java.util.UUID;

public record StockReservationTarget(UUID stockItemId, UUID productVariantId, String sku, String locationCode) {

    public StockReservationTarget {
        Objects.requireNonNull(stockItemId, "stock item id is required");
        Objects.requireNonNull(productVariantId, "product variant id is required");
        sku = requireText(sku, "sku");
        locationCode = requireText(locationCode, "location code");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
