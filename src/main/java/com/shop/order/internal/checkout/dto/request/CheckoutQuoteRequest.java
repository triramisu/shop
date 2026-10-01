package com.shop.order.internal.checkout.dto.request;

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
public class CheckoutQuoteRequest {

    @NotNull(message = "CHECKOUT_CART_VERSION_REQUIRED")
    @PositiveOrZero(message = "CHECKOUT_CART_VERSION_INVALID")
    Long expectedCartVersion;
}
