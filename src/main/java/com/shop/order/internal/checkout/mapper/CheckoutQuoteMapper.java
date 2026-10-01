package com.shop.order.internal.checkout.mapper;

import com.shop.order.internal.checkout.dto.response.CheckoutQuoteLineResponse;
import com.shop.order.internal.checkout.dto.response.CheckoutQuoteResponse;
import com.shop.order.internal.checkout.pricing.CheckoutPricingBreakdown;
import com.shop.order.internal.checkout.pricing.CheckoutPricingResult;
import com.shop.order.internal.checkout.pricing.PricedCheckoutLine;
import org.springframework.stereotype.Component;

@Component
public class CheckoutQuoteMapper {

    public CheckoutQuoteResponse toResponse(CheckoutPricingResult result) {
        CheckoutPricingBreakdown pricing = result.breakdown();
        return CheckoutQuoteResponse.builder()
                .cartId(result.cartId())
                .cartVersion(result.cartVersion())
                .pricedAt(result.pricedAt())
                .currency(pricing.grandTotal().currencyCode())
                .discountRate(pricing.discountRate())
                .taxRate(pricing.taxRate())
                .lines(pricing.lines().stream().map(this::toLineResponse).toList())
                .subtotal(pricing.subtotal().amount())
                .discount(pricing.discount().amount())
                .tax(pricing.tax().amount())
                .grandTotal(pricing.grandTotal().amount())
                .build();
    }

    private CheckoutQuoteLineResponse toLineResponse(PricedCheckoutLine line) {
        return CheckoutQuoteLineResponse.builder()
                .cartItemId(line.cartItemId())
                .productVariantId(line.productVariantId())
                .sku(line.sku())
                .name(line.name())
                .quantity(line.quantity())
                .unitPrice(line.unitPrice().amount())
                .subtotal(line.subtotal().amount())
                .discount(line.discount().amount())
                .tax(line.tax().amount())
                .total(line.total().amount())
                .build();
    }
}
