package com.shop.order.internal.checkout.mapper;

import com.shop.order.internal.checkout.pricing.CheckoutPricingBreakdown;
import com.shop.order.internal.checkout.pricing.PricedCheckoutLine;
import com.shop.order.internal.entity.OrderItemSnapshotDraft;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderSnapshotMapper {

    public List<OrderItemSnapshotDraft> toDrafts(CheckoutPricingBreakdown pricing) {
        return pricing.lines().stream().map(this::toDraft).toList();
    }

    private OrderItemSnapshotDraft toDraft(PricedCheckoutLine line) {
        return new OrderItemSnapshotDraft(
                line.productVariantId(),
                line.sku(),
                line.name(),
                line.quantity(),
                line.unitPrice().amount(),
                line.subtotal().amount(),
                line.discount().amount(),
                line.tax().amount(),
                line.total().amount(),
                line.total().currencyCode());
    }
}
