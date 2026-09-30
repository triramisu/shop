package com.shop.catalog.internal.storefront.dto.request;

import static com.shop.catalog.internal.constant.CatalogValidationConstants.MAX_PAGE_SIZE;

import com.shop.catalog.internal.dto.request.CatalogSortDirection;
import com.shop.catalog.internal.dto.request.ProductSortField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class StorefrontProductSearchRequest {

    @Size(max = 100, message = "SEARCH_KEYWORD_INVALID")
    String keyword;

    UUID categoryId;

    @Min(value = 0, message = "PAGE_NUMBER_INVALID")
    @Builder.Default
    int page = 0;

    @Min(value = 1, message = "PAGE_SIZE_INVALID")
    @Max(value = MAX_PAGE_SIZE, message = "PAGE_SIZE_INVALID")
    @Builder.Default
    int size = 20;

    @NotNull(message = "CATALOG_SORT_INVALID")
    @Builder.Default
    ProductSortField sortBy = ProductSortField.CREATED_AT;

    @NotNull(message = "CATALOG_SORT_INVALID")
    @Builder.Default
    CatalogSortDirection direction = CatalogSortDirection.DESC;
}
