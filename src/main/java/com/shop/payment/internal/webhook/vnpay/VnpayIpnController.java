package com.shop.payment.internal.webhook.vnpay;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "vnpay")
@Tag(name = "VNPay sandbox", description = "IPN công khai, xác thực bằng HMAC; không dùng JWT")
class VnpayIpnController {
    private final VnpayIpnService service;

    @GetMapping(value = "/api/payments/webhooks/vnpay", produces = "application/json")
    @Operation(
            summary = "Nhận IPN VNPay sandbox",
            description =
                    "Receipt trực tiếp RspCode/Message theo giao thức VNPay: 00 áp dụng, 02 đã xử lý, 01 sai reference, 04 sai tiền, 97 sai chữ ký, 99 lỗi cần retry. Không bọc ApiResponse.")
    ResponseEntity<VnpayIpnReceipt> receive(HttpServletRequest request) {
        VnpayIpnReceipt receipt;
        try {
            receipt = service.receive(VnpayParameters.read(request));
        } catch (IllegalArgumentException exception) {
            receipt = VnpayIpnReceipt.of("99");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt);
    }
}
