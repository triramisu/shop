package com.shop.order.internal.entity;

import static com.shop.order.internal.constant.CartValidationConstants.MAX_DISTINCT_ITEMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartDomainTests {

    @Test
    void normalizesOwnerAndMergesTheSameVariantWithoutStoringPrice() {
        Cart cart = Cart.create("  Customer.One  ");
        UUID variantId = UUID.randomUUID();

        cart.addOrIncrement(variantId, " sku-001 ", 2);
        cart.addOrIncrement(variantId, "SKU-001", 3);

        assertThat(cart.getOwnerSubject()).isEqualTo("customer.one");
        assertThat(cart.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductVariantId()).isEqualTo(variantId);
            assertThat(item.getSku()).isEqualTo("SKU-001");
            assertThat(item.getQuantity()).isEqualTo(5);
        });
        assertThat(cart.getTotalQuantity()).isEqualTo(5);
    }

    @Test
    void rejectsMergedQuantityAboveThePerSkuLimit() {
        Cart cart = Cart.create("quantity-owner");
        UUID variantId = UUID.randomUUID();
        cart.addOrIncrement(variantId, "SKU-LIMIT", 99);

        assertThatThrownBy(() -> cart.addOrIncrement(variantId, "SKU-LIMIT", 1))
                .isInstanceOf(CartLimitExceededException.class)
                .extracting("limitType")
                .isEqualTo(CartLimitExceededException.LimitType.QUANTITY);
        assertThat(cart.getItems())
                .singleElement()
                .extracting(CartItem::getQuantity)
                .isEqualTo(99);
    }

    @Test
    void rejectsMoreThanTheMaximumNumberOfDistinctVariants() {
        Cart cart = Cart.create("item-limit-owner");
        for (int index = 0; index < MAX_DISTINCT_ITEMS; index++) {
            cart.addOrIncrement(UUID.randomUUID(), "SKU-" + String.format("%03d", index), 1);
        }

        assertThatThrownBy(() -> cart.addOrIncrement(UUID.randomUUID(), "SKU-OVERFLOW", 1))
                .isInstanceOf(CartLimitExceededException.class)
                .extracting("limitType")
                .isEqualTo(CartLimitExceededException.LimitType.DISTINCT_ITEMS);
        assertThat(cart.getItems()).hasSize(MAX_DISTINCT_ITEMS);
    }
}
