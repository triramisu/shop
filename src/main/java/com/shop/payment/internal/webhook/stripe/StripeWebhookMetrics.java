package com.shop.payment.internal.webhook.stripe;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class StripeWebhookMetrics {

    private static final String DELIVERY_METRIC = "shop.payment.webhook.deliveries";

    private final MeterRegistry meterRegistry;

    void record(String outcome) {
        meterRegistry
                .counter(DELIVERY_METRIC, "provider", "stripe", "outcome", outcome)
                .increment();
    }
}
