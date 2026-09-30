package com.shop.catalog.inventory;

import java.util.Objects;
import java.util.UUID;

public record CatalogSkuReference(UUID productVariantId, String sku) {

    public CatalogSkuReference {
        Objects.requireNonNull(productVariantId, "product variant id is required");
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
    }
}
