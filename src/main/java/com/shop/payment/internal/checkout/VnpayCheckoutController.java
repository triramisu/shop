package com.shop.payment.internal.checkout;

import com.shop.payment.internal.webhook.vnpay.VnpayParameters;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import com.shop.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "vnpay")
@Tag(name = "VNPay sandbox")
class VnpayCheckoutController {
    private final VnpayCheckoutService service;

    @GetMapping("/api/payments/checkout/vnpay/start")
    @Operation(
            summary = "Mở hosted checkout VNPay bằng handoff token",
            description =
                    "URL do backend cấp từ đơn hàng; không nhận giá từ client. Token hết hạn theo createdAt của attempt. Trả redirect 303; token sai/hết hạn trả 400.")
    ResponseEntity<Void> start(@RequestParam UUID attempt, @RequestParam String token, HttpServletRequest request) {
        URI location = service.start(attempt, token, request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(location)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    @GetMapping("/api/payments/checkout/vnpay/return")
    @Operation(
            summary = "Kiểm tra browser return, KHÔNG cập nhật thanh toán",
            description =
                    "Chỉ xác minh dữ liệu trả về; khách phải đọc trạng thái đơn qua API có JWT. Chữ ký sai trả 400; responseCode=00 ở browser không có quyền xác nhận tiền.")
    ResponseEntity<ApiResponse<VnpayBrowserReturnReceipt>> returned(HttpServletRequest request) {
        try {
            var fields = VnpayParameters.read(request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("Referrer-Policy", "no-referrer")
                    .body(ApiResponse.<VnpayBrowserReturnReceipt>builder()
                            .result(service.verifyReturn(fields))
                            .build());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.PAYMENT_CHECKOUT_RETURN_INVALID);
        }
    }
}
