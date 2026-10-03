package com.shop.payment.provider;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public record PaymentProviderResult(
        PaymentProviderStatus status, String providerReference, URI actionUrl, String failureCode) {

    public PaymentProviderResult {
        Objects.requireNonNull(status, "provider payment status is required");
        providerReference = normalizeOptional(providerReference, 150, "provider reference");
        failureCode = normalizeFailureCode(failureCode);

        if (status != PaymentProviderStatus.UNKNOWN && providerReference == null) {
            throw new IllegalArgumentException("provider reference is required for a definitive provider result");
        }
        if (status == PaymentProviderStatus.REQUIRES_ACTION) {
            requireActionUrl(actionUrl);
        } else if (actionUrl != null) {
            throw new IllegalArgumentException("action URL is only valid when provider action is required");
        }
        if (status == PaymentProviderStatus.FAILED) {
            if (failureCode == null) {
                throw new IllegalArgumentException("provider failure code is required for a failed payment");
            }
        } else if (failureCode != null) {
            throw new IllegalArgumentException("provider failure code is only valid for a failed payment");
        }
    }

    private static String normalizeFailureCode(String value) {
        String normalized = normalizeOptional(value, 100, "provider failure code");
        if (normalized != null) {
            normalized = normalized.toUpperCase(Locale.ROOT);
            if (!normalized.matches("[A-Z0-9][A-Z0-9_.-]*")) {
                throw new IllegalArgumentException("provider failure code is invalid");
            }
        }
        return normalized;
    }

    private static void requireActionUrl(URI value) {
        if (value == null || !value.isAbsolute() || value.isOpaque() || value.getHost() == null) {
            throw new IllegalArgumentException("absolute hierarchical provider action URL with a host is required");
        }
        if (!"https".equalsIgnoreCase(value.getScheme())) {
            throw new IllegalArgumentException("provider action URL must use HTTPS");
        }
        if (value.getUserInfo() != null) {
            throw new IllegalArgumentException("provider action URL must not contain user information");
        }
    }

    private static String normalizeOptional(String value, int maximumLength, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalized;
    }
}
