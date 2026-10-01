package com.shop.order.internal.checkout.controller;

import static com.shop.order.internal.constant.OrderApiPaths.CHECKOUT_BASE;
import static com.shop.order.internal.constant.OrderApiPaths.QUOTE;

import com.shop.order.internal.checkout.dto.request.CheckoutQuoteRequest;
import com.shop.order.internal.checkout.dto.response.CheckoutQuoteResponse;
import com.shop.order.internal.checkout.service.CheckoutPricingService;
import com.shop.shared.web.ApiResponse;
import com.shop.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CHECKOUT_BASE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@Tag(name = "Checkout", description = "Tính giá giỏ hàng hoàn toàn từ dữ liệu phía server")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutController {

    CheckoutPricingService checkoutPricingService;

    @PostMapping(QUOTE)
    @Operation(summary = "Tạo báo giá hiện thời cho giỏ hàng của người dùng")
    ApiResponse<CheckoutQuoteResponse> quote(Principal principal, @Valid @RequestBody CheckoutQuoteRequest request) {
        return ApiResponse.<CheckoutQuoteResponse>builder()
                .result(checkoutPricingService.quote(principal.getName(), request))
                .build();
    }
}
