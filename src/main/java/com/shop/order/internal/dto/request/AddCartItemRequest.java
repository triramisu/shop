package com.shop.order.internal.dto.request;

import static com.shop.order.internal.constant.CartValidationConstants.MAX_ITEM_QUANTITY;
import static com.shop.order.internal.constant.CartValidationConstants.MAX_SKU_LENGTH;
import static com.shop.order.internal.constant.CartValidationConstants.MIN_ITEM_QUANTITY;
import static com.shop.order.internal.constant.CartValidationConstants.SKU_PATTERN;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class AddCartItemRequest {

    @NotBlank(message = "CART_SKU_REQUIRED")
    @Size(max = MAX_SKU_LENGTH, message = "CART_SKU_INVALID")
    @Pattern(regexp = SKU_PATTERN, message = "CART_SKU_INVALID")
    String sku;

    @NotNull(message = "CART_QUANTITY_REQUIRED")
    @Min(value = MIN_ITEM_QUANTITY, message = "CART_QUANTITY_INVALID")
    @Max(value = MAX_ITEM_QUANTITY, message = "CART_QUANTITY_INVALID")
    Integer quantity;
}
