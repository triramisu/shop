package com.shop.catalog.internal.dto.response;

import com.shop.catalog.internal.entity.ProductStatus;
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
public class ProductResponse {
    UUID id;
    CategoryResponse category;
    String name;
    String slug;
    String description;
    ProductStatus status;
    long version;
    Instant createdAt;
    Instant updatedAt;
    List<ProductVariantResponse> variants;
}
