package com.shop.order.internal.checkout.pricing;

import java.util.UUID;

public record PricedCheckoutLine(
        UUID cartItemId,
        UUID productVariantId,
        String sku,
        String name,
        int quantity,
        Money unitPrice,
        Money subtotal,
        Money discount,
        Money tax,
        Money total) {}
