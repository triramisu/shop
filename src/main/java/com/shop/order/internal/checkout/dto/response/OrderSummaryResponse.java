package com.shop.order.internal.checkout.dto.response;

import com.shop.order.event.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        OrderStatus status,
        Instant createdAt,
        String currency,
        BigDecimal grandTotal,
        int itemCount,
        int totalQuantity,
        String firstProductName) {}
