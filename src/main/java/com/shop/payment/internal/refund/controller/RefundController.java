package com.shop.payment.internal.refund.controller;

import com.shop.payment.internal.refund.constant.RefundConstants;
import com.shop.payment.internal.refund.dto.CreateRefundRequest;
import com.shop.payment.internal.refund.dto.RefundResponse;
import com.shop.payment.internal.refund.service.RefundService;
import com.shop.shared.web.ApiResponse;
import com.shop.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RefundConstants.ADMIN_BASE)
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + RefundConstants.AUTHORITY + "')")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@Tag(name = "Payment refunds", description = "Hoàn tiền có kiểm soát; cần PAYMENT_REFUND")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Thiếu PAYMENT_REFUND"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "UUID/key/amount/currency sai"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Không tìm thấy payment/refund")
})
public class RefundController {
    private final RefundService service;

    @PostMapping
    @Operation(
            summary = "Yêu cầu hoàn tiền hoặc đọc lại yêu cầu trùng",
            description = "Bỏ/null amount: hoàn phần chưa được giữ/hoàn. Key cùng payload replay trạng thái hiện tại. "
                    + "Chỉ fake ở B.1; mặc định tắt. UNKNOWN/CREATED cần đối soát, không gửi key mới để thử lại.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Kết quả hiện tại, không khẳng định luôn thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Chưa thu/vượt ngân sách/key khác payload"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "Flag tắt/provider chưa hỗ trợ hoặc DB bận; tra cứu trước retry")
    })
    public ResponseEntity<ApiResponse<RefundResponse>> create(
            @PathVariable UUID paymentAttemptId,
            @Parameter(required = true, description = "ASCII 1–100 ký tự, scope payment, phân biệt hoa/thường")
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String key,
            @Valid @RequestBody CreateRefundRequest request) {
        return response(service.create(paymentAttemptId, key, request));
    }

    @GetMapping("/{refundId}")
    @Operation(
            summary = "Tra cứu trạng thái hiện tại của khoản hoàn",
            description = "Vẫn hoạt động khi tắt tạo mới. Refund sai payment và không tồn tại cùng 404.")
    public ResponseEntity<ApiResponse<RefundResponse>> get(
            @PathVariable UUID paymentAttemptId, @PathVariable UUID refundId) {
        return response(service.get(paymentAttemptId, refundId));
    }

    private ResponseEntity<ApiResponse<RefundResponse>> response(RefundResponse result) {
        return ResponseEntity.ok()
                .header("Cache-Control", "private, no-store")
                .body(ApiResponse.<RefundResponse>builder().result(result).build());
    }
}
