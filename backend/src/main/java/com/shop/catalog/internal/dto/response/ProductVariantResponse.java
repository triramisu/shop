package com.shop.catalog.internal.dto.response;

import com.shop.catalog.internal.entity.ProductVariantStatus;
import java.math.BigDecimal;
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
public class ProductVariantResponse {
    UUID id;
    String sku;
    String name;
    BigDecimal price;
    String currency;
    ProductVariantStatus status;
    long version;
    Instant createdAt;
    Instant updatedAt;
}
