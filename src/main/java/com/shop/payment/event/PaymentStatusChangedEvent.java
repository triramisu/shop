package com.shop.payment.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record PaymentStatusChangedEvent(
        UUID eventId,
        UUID paymentAttemptId,
        UUID orderId,
        PaymentStatus previousStatus,
        PaymentStatus currentStatus,
        BigDecimal amount,
        String currency,
        String providerCode,
        String providerReference,
        String failureCode,
        Instant occurredAt) {

    public PaymentStatusChangedEvent {
        Objects.requireNonNull(eventId, "event id is required");
        Objects.requireNonNull(paymentAttemptId, "payment attempt id is required");
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(previousStatus, "previous payment status is required");
        Objects.requireNonNull(currentStatus, "current payment status is required");
        if (previousStatus == currentStatus) {
            throw new IllegalArgumentException("payment status change must change the status");
        }
        amount = normalizeAmount(amount);
        currency = normalizeCurrency(currency);
        providerCode = requireText(providerCode, 50, "provider code").toUpperCase(Locale.ROOT);
        if (providerCode.length() < 2 || !providerCode.matches("[A-Z0-9][A-Z0-9_-]*")) {
            throw new IllegalArgumentException("provider code is invalid");
        }
        providerReference = normalizeOptional(providerReference, 150, "provider reference");
        failureCode = normalizeFailureCode(failureCode);
        if (requiresProviderReference(currentStatus) && providerReference == null) {
            throw new IllegalArgumentException("provider reference is required for payment status " + currentStatus);
        }
        if (currentStatus == PaymentStatus.FAILED && failureCode == null) {
            throw new IllegalArgumentException("failure code is required for a failed payment");
        }
        if (currentStatus != PaymentStatus.FAILED && failureCode != null) {
            throw new IllegalArgumentException("failure code is only valid for a failed payment");
        }
        Objects.requireNonNull(occurredAt, "occurrence time is required");
    }

    private static boolean requiresProviderReference(PaymentStatus status) {
        return status == PaymentStatus.PENDING
                || status == PaymentStatus.REQUIRES_ACTION
                || status == PaymentStatus.SUCCEEDED;
    }

    private static String normalizeFailureCode(String value) {
        String normalized = normalizeOptional(value, 100, "failure code");
        if (normalized != null) {
            normalized = normalized.toUpperCase(Locale.ROOT);
            if (!normalized.matches("[A-Z0-9][A-Z0-9_.-]*")) {
                throw new IllegalArgumentException("failure code is invalid");
            }
        }
        return normalized;
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
        String normalized = requireText(value, 3, "payment currency").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("payment currency is invalid");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maximumLength, String fieldName) {
        return value == null ? null : requireText(value, maximumLength, fieldName);
    }

    private static String requireText(String value, int maximumLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalized;
    }
}
