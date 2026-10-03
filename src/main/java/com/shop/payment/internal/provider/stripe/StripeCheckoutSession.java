package com.shop.payment.internal.provider.stripe;

import java.net.URI;

record StripeCheckoutSession(
        String id,
        URI checkoutUrl,
        String status,
        String paymentStatus,
        String clientReferenceId,
        long amountTotal,
        String currency) {}
