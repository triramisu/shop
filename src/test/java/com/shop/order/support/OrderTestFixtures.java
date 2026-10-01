package com.shop.order.support;

import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.entity.OrderItemSnapshotDraft;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderTestFixtures {

    private OrderTestFixtures() {}

    public static CustomerOrder pendingOrder(String ownerSubject, Instant createdAt) {
        return CustomerOrder.createPending(ownerSubject, List.of(itemDraft()), createdAt);
    }

    public static OrderItemSnapshotDraft itemDraft() {
        return new OrderItemSnapshotDraft(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "TEST-SKU-01",
                "Sản phẩm kiểm thử",
                2,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("2.00"),
                new BigDecimal("1.44"),
                new BigDecimal("19.44"),
                "USD");
    }
}
