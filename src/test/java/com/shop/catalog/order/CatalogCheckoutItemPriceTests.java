package com.shop.catalog.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogCheckoutItemPriceTests {

    @Test
    void normalizesTheCurrencyAndRejectsInvalidCatalogPricingData() {
        CatalogCheckoutItemPrice price = new CatalogCheckoutItemPrice(
                UUID.randomUUID(), "SKU-001", "Sản phẩm", new BigDecimal("10.25"), " usd ", 2);

        assertThat(price.currency()).isEqualTo("USD");
        assertThat(price.unitPrice()).isEqualByComparingTo("10.25");
        assertThatThrownBy(() -> new CatalogCheckoutItemPrice(
                        UUID.randomUUID(), "SKU-001", "Sản phẩm", new BigDecimal("-0.01"), "USD", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogCheckoutItemPrice(
                        UUID.randomUUID(), "SKU-001", "Sản phẩm", BigDecimal.ONE, "INVALID", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogCheckoutItemPrice(
                        UUID.randomUUID(), "SKU-001", "Sản phẩm", BigDecimal.ONE, "USD", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
