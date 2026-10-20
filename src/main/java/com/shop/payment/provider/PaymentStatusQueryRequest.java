package com.shop.payment.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Values must come from the persisted attempt, including the original checkout creation time. */
public record PaymentStatusQueryRequest(
        UUID paymentAttemptId, UUID orderId, BigDecimal amount, String currency, Instant createdAt) {
    public PaymentStatusQueryRequest {
        Objects.requireNonNull(paymentAttemptId, "payment attempt id is required");
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(createdAt, "original checkout creation time is required");
        amount = Objects.requireNonNull(amount, "payment amount is required").setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0 || amount.precision() > 19 || currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("payment query input is invalid");
        }
    }

    @Override
    public String toString() {
        return "PaymentStatusQueryRequest[redacted]";
    }
}
