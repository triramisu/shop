package com.shop.catalog.order;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record CatalogCheckoutItemPrice(
        UUID productVariantId, String sku, String name, BigDecimal unitPrice, String currency, long version) {

    public CatalogCheckoutItemPrice {
        Objects.requireNonNull(productVariantId, "product variant id is required");
        sku = requireText(sku, "sku");
        name = requireText(name, "name");
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unit price must not be negative");
        }
        currency = requireCurrency(currency);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private static String requireCurrency(String value) {
        String normalized = requireText(value, "currency").toUpperCase(Locale.ROOT);
        Currency.getInstance(normalized);
        return normalized;
    }
}
