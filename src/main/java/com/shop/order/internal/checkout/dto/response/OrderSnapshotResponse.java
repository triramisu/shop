package com.shop.order.internal.checkout.dto.response;

import com.shop.order.event.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderSnapshotResponse {
    UUID id;
    OrderStatus status;
    long version;
    Instant createdAt;
    String currency;
    BigDecimal subtotal;
    BigDecimal discount;
    BigDecimal tax;
    BigDecimal grandTotal;
    List<OrderItemSnapshotResponse> items;
}
