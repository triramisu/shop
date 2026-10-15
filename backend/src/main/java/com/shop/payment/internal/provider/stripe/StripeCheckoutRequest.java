package com.shop.payment.internal.provider.stripe;

import java.util.List;
import java.util.Map;
import java.util.UUID;

record StripeCheckoutRequest(
        UUID paymentAttemptId,
        UUID orderId,
        String idempotencyKey,
        long amountInMinorUnit,
        String currency,
        List<Map.Entry<String, String>> formFields) {}
