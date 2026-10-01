package com.shop.order.internal.dto.request;

import static com.shop.order.internal.constant.CartValidationConstants.MAX_ITEM_QUANTITY;
import static com.shop.order.internal.constant.CartValidationConstants.MIN_ITEM_QUANTITY;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class UpdateCartItemRequest {

    @NotNull(message = "CART_QUANTITY_REQUIRED")
    @Min(value = MIN_ITEM_QUANTITY, message = "CART_QUANTITY_INVALID")
    @Max(value = MAX_ITEM_QUANTITY, message = "CART_QUANTITY_INVALID")
    Integer quantity;
}
