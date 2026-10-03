package com.shop.payment.internal.provider.stripe;

import java.util.UUID;

public interface StripeCheckoutStateAccess {

    UUID verify(String token);
}
