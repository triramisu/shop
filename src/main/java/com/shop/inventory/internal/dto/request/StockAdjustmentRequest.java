package com.shop.inventory.internal.dto.request;

import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_REASON_LENGTH;
import static com.shop.inventory.internal.constant.InventoryValidationConstants.MAX_REFERENCE_ID_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class StockAdjustmentRequest {

    @NotNull(message = "INVENTORY_QUANTITY_REQUIRED")
    Long quantityDelta;

    @NotBlank(message = "INVENTORY_REASON_REQUIRED")
    @Size(max = MAX_REASON_LENGTH, message = "INVENTORY_REASON_INVALID")
    String reason;

    @Size(max = MAX_REFERENCE_ID_LENGTH, message = "INVENTORY_REFERENCE_INVALID")
    String referenceId;

    @NotNull(message = "INVENTORY_VERSION_REQUIRED")
    @PositiveOrZero(message = "INVENTORY_VERSION_INVALID")
    Long version;
}
