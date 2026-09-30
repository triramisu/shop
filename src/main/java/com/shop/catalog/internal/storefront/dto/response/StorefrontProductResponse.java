package com.shop.catalog.internal.storefront.dto.response;

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
public class StorefrontProductResponse {
    UUID id;
    StorefrontCategoryResponse category;
    String name;
    String slug;
    String description;
    List<StorefrontProductVariantResponse> variants;
}
