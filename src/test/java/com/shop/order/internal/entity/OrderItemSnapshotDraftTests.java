package com.shop.order.internal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderItemSnapshotDraftTests {

    @Test
    void normalizesTextAndCurrencyWhilePreservingTheReconciledAmounts() {
        OrderItemSnapshotDraft draft = draft(" usd ", "20.00", "2.00", "1.44", "19.44");

        assertThat(draft.sku()).isEqualTo("TEST-SKU");
        assertThat(draft.productName()).isEqualTo("Sản phẩm");
        assertThat(draft.currency()).isEqualTo("USD");
        assertThat(draft.total()).isEqualByComparingTo("19.44");
    }

    @Test
    void rejectsAnUnreconciledOrUnsupportedSnapshot() {
        assertThatThrownBy(() -> draft("USD", "19.99", "2.00", "1.44", "19.44"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subtotal");
        assertThatThrownBy(() -> draft("USD", "20.00", "21.00", "1.44", "0.44"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discount");
        assertThatThrownBy(() -> draft("USD", "20.00", "2.00", "1.44", "19.43"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total");
        assertThatThrownBy(() -> draft("INVALID", "20.00", "2.00", "1.44", "19.44"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OrderItemSnapshotDraft draft(String currency, String subtotal, String discount, String tax, String total) {
        return new OrderItemSnapshotDraft(
                UUID.randomUUID(),
                " TEST-SKU ",
                " Sản phẩm ",
                2,
                new BigDecimal("10.00"),
                new BigDecimal(subtotal),
                new BigDecimal(discount),
                new BigDecimal(tax),
                new BigDecimal(total),
                currency);
    }
}
