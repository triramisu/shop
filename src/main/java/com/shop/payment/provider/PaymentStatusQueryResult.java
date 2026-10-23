package com.shop.payment.provider;

import java.time.Instant;
import java.util.Objects;

/** An authenticated observation, not authority to apply a local state transition. */
public record PaymentStatusQueryResult(
        PaymentProviderStatus status,
        String providerReference,
        String providerTransactionNo,
        Instant observedAt,
        String providerResponseId,
        String receiptHash) {
    public PaymentStatusQueryResult {
        Objects.requireNonNull(status, "query status is required");
        Objects.requireNonNull(observedAt, "observation time is required");
        if (status == PaymentProviderStatus.REQUIRES_ACTION
                || providerReference == null
                || !providerReference.matches("[A-Za-z0-9]{1,100}")
                || providerTransactionNo == null
                || !providerTransactionNo.matches("[A-Za-z0-9]{1,100}")
                || providerResponseId == null
                || !providerResponseId.matches("[A-Za-z0-9]{1,32}")
                || receiptHash == null
                || !receiptHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("payment query result is invalid");
        }
    }

    @Override
    public String toString() {
        return "PaymentStatusQueryResult[status=" + status + ", observedAt=" + observedAt + ", identifiers=redacted]";
    }
}
