package com.shop.order.internal.checkout.controller;

import static com.shop.order.internal.constant.OrderApiPaths.CUSTOMER_ORDERS_BASE;

import com.shop.order.internal.checkout.dto.request.OrderHistoryRequest;
import com.shop.order.internal.checkout.dto.response.OrderHistoryResponse;
import com.shop.order.internal.checkout.dto.response.OrderPaymentResponse;
import com.shop.order.internal.checkout.dto.response.OrderSnapshotResponse;
import com.shop.order.internal.checkout.service.OrderPaymentQueryService;
import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.shared.web.ApiResponse;
import com.shop.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CUSTOMER_ORDERS_BASE)
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@Tag(name = "Customer orders", description = "Chỉ đọc đơn hàng của chính tài khoản đã đăng nhập")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final OrderSnapshotQueryService queryService;
    private final OrderPaymentQueryService paymentQueryService;

    @GetMapping("/{orderId}/payment")
    @Operation(
            summary = "Lấy trạng thái và link thanh toán đơn của tôi",
            description =
                    "Chỉ chủ đơn được đọc; đơn khác owner trả 404/1310. Chưa có attempt trả 404/1407, có thể thử lại sau khi auto-initiation hoàn tất. Link sandbox không phải xác nhận đã trả tiền.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Trạng thái payment và actionUrl nếu cần tiếp tục"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy đơn của tôi hoặc chưa có payment")
    })
    public ResponseEntity<ApiResponse<OrderPaymentResponse>> payment(Principal principal, @PathVariable UUID orderId) {
        return privateResponse(paymentQueryService.getOwnedPayment(principal.getName(), orderId));
    }

    @GetMapping
    @Operation(
            summary = "Lịch sử đơn của tôi",
            description =
                    "Page 0–10000, size 1–50. Cố định createdAt DESC, id DESC; không nhận owner hoặc sort từ client.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Danh sách theo chủ sở hữu, có thể rỗng"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Page/size không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<ApiResponse<OrderHistoryResponse>> history(
            Principal principal, @Valid @ModelAttribute @ParameterObject OrderHistoryRequest request) {
        return privateResponse(queryService.getOwnedOrders(principal.getName(), request.getPage(), request.getSize()));
    }

    @GetMapping("/{orderId}")
    @Operation(
            summary = "Chi tiết đơn của tôi",
            description = "Đơn không tồn tại và đơn khác chủ sở hữu đều trả 404/1310; không có ngoại lệ cho ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Snapshot và trạng thái hiện tại của đơn"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy đơn của tài khoản này, code 1310")
    })
    public ResponseEntity<ApiResponse<OrderSnapshotResponse>> detail(
            Principal principal,
            @Parameter(description = "UUID của đơn thuộc tài khoản hiện tại") @PathVariable UUID orderId) {
        return privateResponse(queryService.getOwnedOrder(principal.getName(), orderId));
    }

    private <T> ResponseEntity<ApiResponse<T>> privateResponse(T result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(ApiResponse.<T>builder().result(result).build());
    }
}
