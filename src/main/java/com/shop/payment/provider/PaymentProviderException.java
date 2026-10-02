package com.shop.payment.provider;

import lombok.Getter;

@Getter
public class PaymentProviderException extends RuntimeException {

    private static final int MAX_ERROR_CODE_LENGTH = 100;

    private final PaymentProviderErrorType errorType;
    private final String providerErrorCode;

    public PaymentProviderException(PaymentProviderErrorType errorType, String providerErrorCode) {
        super("payment provider operation failed: "
                + requireErrorType(errorType).name());
        this.errorType = errorType;
        this.providerErrorCode = normalizeErrorCode(providerErrorCode);
    }

    public boolean isRetryable() {
        return errorType.isRetryable();
    }

    private static PaymentProviderErrorType requireErrorType(PaymentProviderErrorType value) {
        if (value == null) {
            throw new IllegalArgumentException("provider error type is required");
        }
        return value;
    }

    private static String normalizeErrorCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider error code is required");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_ERROR_CODE_LENGTH || !normalized.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException("provider error code is invalid");
        }
        return normalized;
    }
}
