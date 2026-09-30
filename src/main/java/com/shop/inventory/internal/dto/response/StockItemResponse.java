package com.shop.inventory.internal.dto.response;

import java.time.Instant;
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
public class StockItemResponse {
    UUID id;
    UUID productVariantId;
    String sku;
    String locationCode;
    long onHand;
    long reserved;
    long available;
    long version;
    Instant createdAt;
    Instant updatedAt;
}
