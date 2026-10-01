package com.shop.order.internal.checkout.orchestration;

import java.util.UUID;

public record OrderInventoryReservationLineSnapshot(
        UUID reservationId,
        UUID orderItemId,
        UUID productVariantId,
        int lineNumber,
        String sku,
        long quantity,
        UUID stockItemId,
        InventoryReservationLineStatus status,
        String failureCode) {}
