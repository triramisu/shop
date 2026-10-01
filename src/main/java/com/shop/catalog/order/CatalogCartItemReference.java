package com.shop.catalog.order;

import java.util.Objects;
import java.util.UUID;

public record CatalogCartItemReference(UUID productVariantId, String sku) {

    public CatalogCartItemReference {
        Objects.requireNonNull(productVariantId, "product variant id is required");
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
    }
}
