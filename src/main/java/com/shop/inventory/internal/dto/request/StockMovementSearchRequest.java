package com.shop.inventory.internal.dto.request;

import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_PAGE_SIZE;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class StockMovementSearchRequest {

    @Min(value = 0, message = "PAGE_NUMBER_INVALID")
    @Builder.Default
    int page = 0;

    @Min(value = 1, message = "PAGE_SIZE_INVALID")
    @Max(value = MAX_PAGE_SIZE, message = "PAGE_SIZE_INVALID")
    @Builder.Default
    int size = 20;
}
