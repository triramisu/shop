package com.shop.order.internal.entity;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record OrderItemSnapshotDraft(
        UUID productVariantId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total,
        String currency) {

    public OrderItemSnapshotDraft {
        Objects.requireNonNull(productVariantId, "product variant id is required");
        sku = requireText(sku, "sku", 100);
        productName = requireText(productName, "product name", 200);
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        unitPrice = requireNonNegative(unitPrice, "unit price");
        subtotal = requireNonNegative(subtotal, "subtotal");
        discount = requireNonNegative(discount, "discount");
        tax = requireNonNegative(tax, "tax");
        total = requireNonNegative(total, "total");
        currency = normalizeCurrency(currency);
        if (subtotal.compareTo(unitPrice.multiply(BigDecimal.valueOf(quantity))) != 0) {
            throw new IllegalArgumentException("subtotal must equal unit price multiplied by quantity");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("discount must not exceed subtotal");
        }
        if (total.compareTo(subtotal.subtract(discount).add(tax)) != 0) {
            throw new IllegalArgumentException("total must equal subtotal minus discount plus tax");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.signum() < 0 || value.scale() > 4 || value.precision() - value.scale() > 15) {
            throw new IllegalArgumentException(field + " is outside the supported monetary range");
        }
        return value;
    }

    private static String normalizeCurrency(String value) {
        String normalized = requireText(value, "currency", 3).toUpperCase(Locale.ROOT);
        Currency.getInstance(normalized);
        return normalized;
    }
}
