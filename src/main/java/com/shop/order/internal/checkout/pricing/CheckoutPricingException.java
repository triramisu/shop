package com.shop.order.internal.checkout.pricing;

import lombok.Getter;

@Getter
public class CheckoutPricingException extends RuntimeException {

    private final Reason reason;

    CheckoutPricingException(Reason reason) {
        super("Checkout pricing rejected: " + reason);
        this.reason = reason;
    }

    public enum Reason {
        MIXED_CURRENCY
    }
}
