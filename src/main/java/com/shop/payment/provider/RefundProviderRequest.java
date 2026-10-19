package com.shop.payment.provider;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RefundProviderRequest(
        UUID refundId,
        UUID paymentAttemptId,
        UUID orderId,
        String providerPaymentReference,
        BigDecimal amount,
        String currency,
        String idempotencyKey) {
    public RefundProviderRequest {
        Objects.requireNonNull(refundId);
        Objects.requireNonNull(paymentAttemptId);
        Objects.requireNonNull(orderId);
        if (providerPaymentReference == null
                || providerPaymentReference.isBlank()
                || providerPaymentReference.length() > 150
                || amount == null
                || amount.signum() <= 0
                || amount.scale() > 2
                || amount.precision() > 19
                || currency == null
                || !currency.matches("[A-Z]{3}")
                || idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("Yêu cầu hoàn tiền của provider không hợp lệ");
        }
    }
}
