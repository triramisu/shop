package com.shop.catalog.internal.dto.request;

import static com.shop.catalog.internal.constant.CatalogValidationConstants.CURRENCY_PATTERN;
import static com.shop.catalog.internal.constant.CatalogValidationConstants.MAX_SKU_LENGTH;
import static com.shop.catalog.internal.constant.CatalogValidationConstants.MAX_VARIANT_NAME_LENGTH;
import static com.shop.catalog.internal.constant.CatalogValidationConstants.SKU_PATTERN;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
public class CreateProductVariantRequest {

    @NotBlank(message = "SKU_REQUIRED")
    @Size(max = MAX_SKU_LENGTH, message = "SKU_INVALID")
    @Pattern(regexp = SKU_PATTERN, message = "SKU_INVALID")
    String sku;

    @NotBlank(message = "CATALOG_NAME_REQUIRED")
    @Size(max = MAX_VARIANT_NAME_LENGTH, message = "CATALOG_NAME_INVALID")
    String name;

    @NotNull(message = "PRICE_REQUIRED")
    @DecimalMin(value = "0.00", message = "PRICE_INVALID")
    @Digits(integer = 17, fraction = 2, message = "PRICE_INVALID")
    BigDecimal price;

    @NotBlank(message = "CURRENCY_REQUIRED")
    @Pattern(regexp = CURRENCY_PATTERN, message = "CURRENCY_INVALID")
    String currency;

    @NotNull(message = "CATALOG_VERSION_REQUIRED")
    @PositiveOrZero(message = "CATALOG_VERSION_INVALID")
    Long productVersion;
}
