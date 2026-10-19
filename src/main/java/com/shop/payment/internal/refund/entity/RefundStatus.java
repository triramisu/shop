package com.shop.payment.internal.refund.entity;

public enum RefundStatus {
    CREATED,
    PENDING,
    UNKNOWN,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
