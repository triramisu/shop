package com.shop.catalog.internal.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

final class CatalogDomainRules {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]*");
    private static final Pattern SKU_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._-]{2,99}");
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private CatalogDomainRules() {}

    static String requiredText(String value, String field, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " length is invalid");
        }
        return normalized;
    }

    static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " length is invalid");
        }
        return normalized;
    }

    static String code(String value, String field, int maxLength) {
        String normalized = requiredText(value, field, maxLength).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " format is invalid");
        }
        return normalized;
    }

    static String sku(String value) {
        String normalized = requiredText(value, "sku", 100).toUpperCase(Locale.ROOT);
        if (!SKU_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("sku format is invalid");
        }
        return normalized;
    }

    static String slug(String value, int maxLength) {
        String normalized = requiredText(value, "slug", maxLength).toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("slug format is invalid");
        }
        return normalized;
    }

    static BigDecimal price(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("price supports at most two decimal places", exception);
        }
        if (normalized.precision() > 19) {
            throw new IllegalArgumentException("price exceeds the supported precision");
        }
        return normalized;
    }

    static String currency(String value) {
        String normalized = requiredText(value, "currency", 3).toUpperCase(Locale.ROOT);
        try {
            Currency currency = Currency.getInstance(normalized);
            if (currency.getDefaultFractionDigits() < 0) {
                throw new IllegalArgumentException("currency is not usable for prices");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currency is invalid", exception);
        }
        return normalized;
    }
}
