package com.shop.order.internal.checkout.pricing;

import com.shop.order.internal.checkout.configuration.CheckoutPricingProperties;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutPricingCalculator {

    CheckoutPricingProperties properties;

    public CheckoutPricingBreakdown calculate(List<CheckoutLineInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("checkout lines are required");
        }

        List<PricedCheckoutLine> lines = new ArrayList<>(inputs.size());
        Currency currency = Money.of(
                        inputs.getFirst().unitPrice(), inputs.getFirst().currency())
                .currency();
        Money subtotal = Money.zero(currency);
        Money discount = Money.zero(currency);
        Money tax = Money.zero(currency);
        Money grandTotal = Money.zero(currency);

        for (CheckoutLineInput input : inputs) {
            Money unitPrice = Money.of(input.unitPrice(), input.currency());
            Money lineSubtotal = unitPrice.multiply(input.quantity());
            Money lineDiscount = lineSubtotal.percentage(properties.getDiscountRate());
            Money discountedSubtotal = lineSubtotal.subtract(lineDiscount);
            Money lineTax = discountedSubtotal.percentage(properties.getTaxRate());
            Money lineTotal = discountedSubtotal.add(lineTax);

            lines.add(new PricedCheckoutLine(
                    input.cartItemId(),
                    input.productVariantId(),
                    input.sku(),
                    input.name(),
                    input.quantity(),
                    unitPrice,
                    lineSubtotal,
                    lineDiscount,
                    lineTax,
                    lineTotal));
            subtotal = subtotal.add(lineSubtotal);
            discount = discount.add(lineDiscount);
            tax = tax.add(lineTax);
            grandTotal = grandTotal.add(lineTotal);
        }

        return new CheckoutPricingBreakdown(
                lines, subtotal, discount, tax, grandTotal, properties.getDiscountRate(), properties.getTaxRate());
    }
}
