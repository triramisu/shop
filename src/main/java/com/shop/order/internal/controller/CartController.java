package com.shop.order.internal.controller;

import static com.shop.order.internal.constant.OrderApiPaths.CART_BASE;
import static com.shop.order.internal.constant.OrderApiPaths.ITEMS;
import static com.shop.order.internal.constant.OrderApiPaths.ITEM_BY_ID;

import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.dto.request.UpdateCartItemRequest;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.service.CartService;
import com.shop.shared.web.ApiResponse;
import com.shop.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CART_BASE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@Tag(name = "Giỏ hàng", description = "Quản lý giỏ hàng của người dùng đang đăng nhập")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartController {

    CartService cartService;

    @GetMapping
    @Operation(summary = "Xem giỏ hàng hiện tại")
    ApiResponse<CartResponse> getCart(Principal principal) {
        return response(cartService.getCart(principal.getName()));
    }

    @PostMapping(ITEMS)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm SKU đang bán vào giỏ hàng")
    ApiResponse<CartResponse> addItem(Principal principal, @Valid @RequestBody AddCartItemRequest request) {
        return response(cartService.addItem(principal.getName(), request));
    }

    @PutMapping(ITEM_BY_ID)
    @Operation(summary = "Đặt lại số lượng của một mục thuộc giỏ hàng hiện tại")
    ApiResponse<CartResponse> updateItem(
            Principal principal, @PathVariable UUID itemId, @Valid @RequestBody UpdateCartItemRequest request) {
        return response(cartService.updateItem(principal.getName(), itemId, request));
    }

    @DeleteMapping(ITEM_BY_ID)
    @Operation(summary = "Xóa một mục thuộc giỏ hàng hiện tại")
    ApiResponse<CartResponse> removeItem(Principal principal, @PathVariable UUID itemId) {
        return response(cartService.removeItem(principal.getName(), itemId));
    }

    @DeleteMapping(ITEMS)
    @Operation(summary = "Xóa toàn bộ mục trong giỏ hàng hiện tại")
    ApiResponse<CartResponse> clear(Principal principal) {
        return response(cartService.clear(principal.getName()));
    }

    private ApiResponse<CartResponse> response(CartResponse result) {
        return ApiResponse.<CartResponse>builder().result(result).build();
    }
}
