package com.shop.payment.internal.service;

import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.processing.PaymentAttemptSnapshot;

final class PaymentAttemptSnapshotMapper {

    private PaymentAttemptSnapshotMapper() {}

    static PaymentAttemptSnapshot toSnapshot(PaymentAttempt attempt) {
        return new PaymentAttemptSnapshot(
                attempt.getId(),
                attempt.getOrderId(),
                attempt.getAttemptNumber(),
                attempt.getAmount(),
                attempt.getCurrency(),
                attempt.getProviderCode(),
                attempt.getProviderReference(),
                attempt.getStatus(),
                attempt.getFailureCode(),
                attempt.getCompletedAt(),
                attempt.getVersion(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt());
    }
}
