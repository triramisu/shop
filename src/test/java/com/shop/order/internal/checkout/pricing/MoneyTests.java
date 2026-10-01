package com.shop.order.internal.checkout.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTests {

    @Test
    void appliesIsoCurrencyScaleAndHalfUpRoundingConsistently() {
        assertThat(Money.of(new BigDecimal("10.125"), "USD").amount()).isEqualByComparingTo("10.13");
        assertThat(Money.of(new BigDecimal("10.50"), "VND").amount()).isEqualByComparingTo("11");
        assertThat(Money.of(new BigDecimal("10.1234"), "BHD").amount()).isEqualByComparingTo("10.123");
    }

    @Test
    void keepsArithmeticInOneCurrencyAndRejectsInvalidOperations() {
        Money base = Money.of(new BigDecimal("19.90"), "USD");

        assertThat(base.multiply(2).amount()).isEqualByComparingTo("39.80");
        assertThat(base.percentage(new BigDecimal("0.10")).amount()).isEqualByComparingTo("1.99");
        assertThat(base.add(Money.of(new BigDecimal("0.10"), "USD")).amount()).isEqualByComparingTo("20.00");
        assertThatThrownBy(() -> base.add(Money.of(BigDecimal.ONE, "EUR")))
                .isInstanceOf(CheckoutPricingException.class)
                .extracting("reason")
                .isEqualTo(CheckoutPricingException.Reason.MIXED_CURRENCY);
        assertThatThrownBy(() -> base.multiply(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base.percentage(new BigDecimal("1.01"))).isInstanceOf(IllegalArgumentException.class);
    }
}
