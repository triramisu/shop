package com.shop.payment.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record PaymentProviderRequest(
        UUID paymentAttemptId,
        UUID orderId,
        int attemptNumber,
        BigDecimal amount,
        String currency,
        String idempotencyKey) {

    public PaymentProviderRequest {
        Objects.requireNonNull(paymentAttemptId, "payment attempt id is required");
        Objects.requireNonNull(orderId, "order id is required");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("payment attempt number must be positive");
        }
        amount = normalizeAmount(amount);
        currency = normalizeCurrency(currency);
        idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
    }

    private static BigDecimal normalizeAmount(BigDecimal value) {
        BigDecimal normalized =
                Objects.requireNonNull(value, "payment amount is required").setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.signum() <= 0 || normalized.precision() > 19) {
            throw new IllegalArgumentException("payment amount is invalid");
        }
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("payment currency is required");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("payment currency is invalid");
        }
        return normalized;
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.length() < 8 || value.length() > 128) {
            throw new IllegalArgumentException("payment idempotency key length is invalid");
        }
        if (value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException("payment idempotency key must contain visible ASCII characters");
        }
        return value;
    }
}
