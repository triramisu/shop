package com.shop.payment.internal.reconciliation;

import com.shop.payment.provider.PaymentStatusQueryRequest;
import java.time.Instant;
import java.util.UUID;

record ReconciliationLease(
        UUID token,
        long paymentVersion,
        int queryCount,
        Instant startedAt,
        Instant until,
        PaymentStatusQueryRequest request) {
    @Override
    public String toString() {
        return "ReconciliationLease[redacted]";
    }
}
