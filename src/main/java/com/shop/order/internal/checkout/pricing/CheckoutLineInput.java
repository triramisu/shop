package com.shop.order.internal.checkout.pricing;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record CheckoutLineInput(
        UUID cartItemId,
        UUID productVariantId,
        String sku,
        String name,
        int quantity,
        BigDecimal unitPrice,
        String currency) {

    public CheckoutLineInput {
        Objects.requireNonNull(cartItemId, "cart item id is required");
        Objects.requireNonNull(productVariantId, "product variant id is required");
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(unitPrice, "unit price is required");
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }
}
