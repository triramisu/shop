package com.shop.payment.event;

public enum PaymentStatus {
    CREATED,
    PENDING,
    REQUIRES_ACTION,
    UNKNOWN,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
