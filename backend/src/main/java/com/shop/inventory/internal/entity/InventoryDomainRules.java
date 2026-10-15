package com.shop.inventory.internal.entity;

import java.util.Locale;
import java.util.regex.Pattern;

final class InventoryDomainRules {

    private static final Pattern SKU_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._-]{2,99}");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,63}");

    private InventoryDomainRules() {}

    static String sku(String value) {
        String normalized = normalizedCode(value, "sku");
        if (!SKU_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("sku is invalid");
        }
        return normalized;
    }

    static String locationCode(String value) {
        String normalized = normalizedCode(value, "location code");
        if (!LOCATION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("location code is invalid");
        }
        return normalized;
    }

    static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, field, maxLength);
    }

    static long nonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    static long positive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    static long addExact(long current, long delta, String field) {
        try {
            return Math.addExact(current, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " is out of range", exception);
        }
    }

    private static String normalizedCode(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
