package com.shop.payment.internal.refund.service;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class RefundTestFixtures {
    static PaymentAttempt payment(String provider, boolean captured) {
        Instant now = Instant.now().minusSeconds(1);
        var payment = PaymentAttempt.start(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("100.00"), "USD", provider, now);
        if (captured) {
            payment.transition(PaymentStatus.SUCCEEDED, "payment_" + payment.getId(), null, now);
        }
        return payment;
    }

    private RefundTestFixtures() {}
}
