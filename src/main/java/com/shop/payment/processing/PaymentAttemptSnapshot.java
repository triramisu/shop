package com.shop.payment.processing;

import com.shop.payment.event.PaymentStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptSnapshot(
        UUID id,
        UUID orderId,
        int attemptNumber,
        BigDecimal amount,
        String currency,
        String providerCode,
        String providerReference,
        PaymentStatus status,
        String failureCode,
        URI actionUrl,
        Instant completedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
