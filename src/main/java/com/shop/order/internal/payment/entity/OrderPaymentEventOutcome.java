package com.shop.order.internal.payment.entity;

public enum OrderPaymentEventOutcome {
    RECEIVED,
    PROCESSING,
    COMPLETED,
    IGNORED,
    RETRY_REQUIRED,
    MANUAL_ACTION_REQUIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == IGNORED || this == MANUAL_ACTION_REQUIRED;
    }
}
