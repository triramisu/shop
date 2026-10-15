package com.shop.payment.internal.provider.fake;

import com.shop.payment.provider.PaymentProviderStatus;
import java.time.Instant;

record FakePaymentCallback(
        String eventId,
        String providerReference,
        PaymentProviderStatus status,
        String failureCode,
        Instant occurredAt) {}
