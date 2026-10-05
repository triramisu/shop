package com.shop.payment.internal.webhook.stripe;

public record StripeWebhookReceipt(String status) {

    static StripeWebhookReceipt processed() {
        return new StripeWebhookReceipt("PROCESSED");
    }

    static StripeWebhookReceipt duplicate() {
        return new StripeWebhookReceipt("DUPLICATE");
    }

    static StripeWebhookReceipt ignored() {
        return new StripeWebhookReceipt("IGNORED");
    }
}
