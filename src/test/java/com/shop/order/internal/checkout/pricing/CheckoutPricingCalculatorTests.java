package com.shop.order.internal.checkout.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.order.internal.checkout.configuration.CheckoutPricingProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutPricingCalculatorTests {

    @Test
    void roundsEachLineThenReconcilesTheQuoteTotals() {
        CheckoutPricingProperties properties = new CheckoutPricingProperties();
        properties.setDiscountRate(new BigDecimal("0.10"));
        properties.setTaxRate(new BigDecimal("0.08"));
        CheckoutPricingCalculator calculator = new CheckoutPricingCalculator(properties);

        CheckoutPricingBreakdown result =
                calculator.calculate(List.of(line("SKU-001", 2, "19.90", "USD"), line("SKU-002", 3, "5.55", "USD")));

        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().getFirst().subtotal().amount()).isEqualByComparingTo("39.80");
        assertThat(result.lines().getFirst().discount().amount()).isEqualByComparingTo("3.98");
        assertThat(result.lines().getFirst().tax().amount()).isEqualByComparingTo("2.87");
        assertThat(result.lines().getFirst().total().amount()).isEqualByComparingTo("38.69");
        assertThat(result.subtotal().amount()).isEqualByComparingTo("56.45");
        assertThat(result.discount().amount()).isEqualByComparingTo("5.65");
        assertThat(result.tax().amount()).isEqualByComparingTo("4.07");
        assertThat(result.grandTotal().amount()).isEqualByComparingTo("54.87");
        assertThat(result.lines().stream()
                        .map(PricedCheckoutLine::total)
                        .reduce(Money.zero(result.grandTotal().currency()), Money::add))
                .isEqualTo(result.grandTotal());
    }

    private CheckoutLineInput line(String sku, int quantity, String unitPrice, String currency) {
        return new CheckoutLineInput(
                UUID.randomUUID(),
                UUID.randomUUID(),
                sku,
                "Sản phẩm " + sku,
                quantity,
                new BigDecimal(unitPrice),
                currency);
    }
}
