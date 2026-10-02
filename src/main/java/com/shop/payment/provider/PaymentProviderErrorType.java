package com.shop.payment.provider;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentProviderErrorType {
    TIMEOUT(true),
    TEMPORARY_UNAVAILABLE(true),
    RATE_LIMITED(true),
    PROTOCOL_ERROR(true),
    INVALID_REQUEST(false),
    AUTHENTICATION_FAILED(false),
    CONFIGURATION_ERROR(false);

    private final boolean retryable;
}
