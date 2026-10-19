package com.shop.catalog.internal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
public class UpdateProductVariantStatusRequest {

    @NotNull(message = "CATALOG_STATUS_REQUIRED")
    ProductVariantAvailability status;

    @NotNull(message = "CATALOG_VERSION_REQUIRED")
    @PositiveOrZero(message = "CATALOG_VERSION_INVALID")
    Long version;
}
