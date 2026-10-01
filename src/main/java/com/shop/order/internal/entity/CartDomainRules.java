package com.shop.order.internal.entity;

import static com.shop.order.internal.constant.CartValidationConstants.MAX_ITEM_QUANTITY;
import static com.shop.order.internal.constant.CartValidationConstants.MAX_SKU_LENGTH;
import static com.shop.order.internal.constant.CartValidationConstants.MIN_ITEM_QUANTITY;

import java.util.Locale;
import java.util.regex.Pattern;

final class CartDomainRules {

    private static final Pattern SKU_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._-]{2,99}");

    private CartDomainRules() {}

    static String ownerSubject(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("owner subject is required");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("owner subject is too long");
        }
        return normalized;
    }

    static String sku(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_SKU_LENGTH
                || !SKU_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("sku is invalid");
        }
        return normalized;
    }

    static int quantity(int value) {
        if (value < MIN_ITEM_QUANTITY || value > MAX_ITEM_QUANTITY) {
            throw new CartLimitExceededException(CartLimitExceededException.LimitType.QUANTITY);
        }
        return value;
    }

    static int addQuantity(int current, int delta) {
        try {
            return quantity(Math.addExact(current, delta));
        } catch (ArithmeticException exception) {
            throw new CartLimitExceededException(CartLimitExceededException.LimitType.QUANTITY);
        }
    }
}
