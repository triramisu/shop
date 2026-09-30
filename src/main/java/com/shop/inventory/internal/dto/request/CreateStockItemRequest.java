package com.shop.inventory.internal.dto.request;

import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_LOCATION_CODE_LENGTH;
import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_REASON_LENGTH;
import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_REFERENCE_ID_LENGTH;
import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_SKU_LENGTH;

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
public class CreateStockItemRequest {

    @NotBlank(message = "INVENTORY_SKU_REQUIRED")
    @Size(max = MAX_SKU_LENGTH, message = "INVENTORY_SKU_INVALID")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{2,99}", message = "INVENTORY_SKU_INVALID")
    String sku;

    @NotBlank(message = "INVENTORY_LOCATION_REQUIRED")
    @Size(max = MAX_LOCATION_CODE_LENGTH, message = "INVENTORY_LOCATION_INVALID")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,63}", message = "INVENTORY_LOCATION_INVALID")
    String locationCode;

    @NotNull(message = "INVENTORY_QUANTITY_REQUIRED")
    @PositiveOrZero(message = "INVENTORY_QUANTITY_INVALID")
    Long initialQuantity;

    @Size(max = MAX_REASON_LENGTH, message = "INVENTORY_REASON_INVALID")
    String reason;

    @Size(max = MAX_REFERENCE_ID_LENGTH, message = "INVENTORY_REFERENCE_INVALID")
    String referenceId;
}
