package com.shop.catalog.internal.dto.request;

import static com.shop.catalog.internal.constant.CatalogValidationConstants.MAX_CATEGORY_NAME_LENGTH;
import static com.shop.catalog.internal.constant.CatalogValidationConstants.MAX_CATEGORY_SLUG_LENGTH;
import static com.shop.catalog.internal.constant.CatalogValidationConstants.SLUG_PATTERN;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class UpdateCategoryRequest {

    @NotBlank(message = "CATALOG_NAME_REQUIRED")
    @Size(max = MAX_CATEGORY_NAME_LENGTH, message = "CATALOG_NAME_INVALID")
    String name;

    @NotBlank(message = "CATALOG_SLUG_REQUIRED")
    @Size(max = MAX_CATEGORY_SLUG_LENGTH, message = "CATALOG_SLUG_INVALID")
    @Pattern(regexp = SLUG_PATTERN, message = "CATALOG_SLUG_INVALID")
    String slug;

    @NotNull(message = "CATALOG_VERSION_REQUIRED")
    @PositiveOrZero(message = "CATALOG_VERSION_INVALID")
    Long version;
}
