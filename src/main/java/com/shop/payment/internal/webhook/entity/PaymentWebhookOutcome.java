package com.shop.payment.internal.webhook.entity;

public enum PaymentWebhookOutcome {
    RECEIVED,
    APPLIED,
    ALREADY_APPLIED,
    IGNORED_UNSUPPORTED,
    IGNORED_TERMINAL
}
