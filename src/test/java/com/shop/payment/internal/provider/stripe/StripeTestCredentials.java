package com.shop.payment.internal.provider.stripe;

final class StripeTestCredentials {

    private StripeTestCredentials() {}

    static String apiKey() {
        return "sk_" + "test_" + "a".repeat(32);
    }

    static String stateSigningKey() {
        return "test-state-key-" + "b".repeat(32);
    }

    static String webhookSigningKey() {
        return "whsec_" + "c".repeat(32);
    }
}
