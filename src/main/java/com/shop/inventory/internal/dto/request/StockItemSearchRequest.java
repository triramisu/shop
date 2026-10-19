package com.shop.inventory.internal.dto.request;

import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_LOCATION_CODE_LENGTH;
import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_PAGE_SIZE;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class StockItemSearchRequest {

    @Size(max = 100, message = "SEARCH_KEYWORD_INVALID")
    String keyword;

    @Size(max = MAX_LOCATION_CODE_LENGTH, message = "INVENTORY_LOCATION_INVALID")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,63}", message = "INVENTORY_LOCATION_INVALID")
    String locationCode;

    @Min(value = 0, message = "PAGE_NUMBER_INVALID")
    @Builder.Default
    int page = 0;

    @Min(value = 1, message = "PAGE_SIZE_INVALID")
    @Max(value = MAX_PAGE_SIZE, message = "PAGE_SIZE_INVALID")
    @Builder.Default
    int size = 20;

    @NotNull(message = "INVENTORY_SORT_INVALID")
    @Builder.Default
    StockItemSortField sortBy = StockItemSortField.CREATED_AT;

    @NotNull(message = "INVENTORY_SORT_INVALID")
    @Builder.Default
    InventorySortDirection direction = InventorySortDirection.DESC;
}
