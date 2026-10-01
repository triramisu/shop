package com.shop.order.internal.checkout.pricing;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPricingBreakdown(
        List<PricedCheckoutLine> lines,
        Money subtotal,
        Money discount,
        Money tax,
        Money grandTotal,
        BigDecimal discountRate,
        BigDecimal taxRate) {

    public CheckoutPricingBreakdown {
        lines = List.copyOf(lines);
    }
}
