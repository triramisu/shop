package com.shop.order.internal.checkout.dto.request;

import static com.shop.order.internal.constant.OrderQueryConstants.MAX_PAGE;
import static com.shop.order.internal.constant.OrderQueryConstants.MAX_PAGE_SIZE;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderHistoryRequest {

    @Min(value = 0, message = "PAGE_NUMBER_INVALID")
    @Max(value = MAX_PAGE, message = "PAGE_NUMBER_INVALID")
    private int page;

    @Min(value = 1, message = "PAGE_SIZE_INVALID")
    @Max(value = MAX_PAGE_SIZE, message = "PAGE_SIZE_INVALID")
    private int size = 10;
}
