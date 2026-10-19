package com.shop.payment.internal.checkout;

import com.shop.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/checkout")
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "stripe")
@Tag(name = "Payment checkout", description = "Nhận điều hướng trở lại từ trang thanh toán được Stripe lưu trữ")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HostedCheckoutReturnController {

    StripeCheckoutReturnService checkoutReturnService;

    @GetMapping("/return")
    @Operation(summary = "Xác nhận trình duyệt đã quay lại từ Stripe Checkout")
    ApiResponse<HostedCheckoutReturnResponse> handleReturn(
            @RequestParam(required = false) String state,
            @RequestParam(name = "session_id", required = false) String sessionId) {
        return ApiResponse.<HostedCheckoutReturnResponse>builder()
                .result(checkoutReturnService.handleReturn(state, sessionId))
                .build();
    }

    @GetMapping("/cancel")
    @Operation(summary = "Xác nhận người dùng quay lại từ luồng hủy Stripe Checkout")
    ApiResponse<HostedCheckoutReturnResponse> handleCancel(@RequestParam(required = false) String state) {
        return ApiResponse.<HostedCheckoutReturnResponse>builder()
                .result(checkoutReturnService.handleCancel(state))
                .build();
    }
}
