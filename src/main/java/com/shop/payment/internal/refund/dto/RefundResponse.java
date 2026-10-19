package com.shop.payment.internal.refund.dto;

import com.shop.payment.internal.refund.entity.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentAttemptId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {}
