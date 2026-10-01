package com.shop.order.internal.checkout.dto.response;

import java.math.BigDecimal;
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
public class OrderItemSnapshotResponse {
    UUID id;
    UUID productVariantId;
    int lineNumber;
    String sku;
    String productName;
    int quantity;
    BigDecimal unitPrice;
    BigDecimal subtotal;
    BigDecimal discount;
    BigDecimal tax;
    BigDecimal total;
    String currency;
}
