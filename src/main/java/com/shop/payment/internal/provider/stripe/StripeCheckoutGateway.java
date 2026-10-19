package com.shop.payment.internal.provider.stripe;

interface StripeCheckoutGateway {

    StripeCheckoutSession createSession(StripeCheckoutRequest request);
}
