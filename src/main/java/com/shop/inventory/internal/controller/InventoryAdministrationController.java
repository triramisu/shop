package com.shop.inventory.internal.controller;

import static com.shop.inventory.internal.constant.InventoryApiPaths.ADMIN_BASE;
import static com.shop.inventory.internal.constant.InventoryApiPaths.STOCK_ADJUSTMENTS;
import static com.shop.inventory.internal.constant.InventoryApiPaths.STOCK_ITEMS;
import static com.shop.inventory.internal.constant.InventoryApiPaths.STOCK_ITEM_BY_ID;
import static com.shop.inventory.internal.constant.InventoryApiPaths.STOCK_MOVEMENTS;
import static com.shop.inventory.internal.constant.InventoryAuthority.READ;
import static com.shop.inventory.internal.constant.InventoryAuthority.WRITE;

import com.shop.inventory.internal.dto.request.CreateStockItemRequest;
import com.shop.inventory.internal.dto.request.StockAdjustmentRequest;
import com.shop.inventory.internal.dto.request.StockItemSearchRequest;
import com.shop.inventory.internal.dto.request.StockMovementSearchRequest;
import com.shop.inventory.internal.dto.response.StockItemPageResponse;
import com.shop.inventory.internal.dto.response.StockItemResponse;
import com.shop.inventory.internal.dto.response.StockMovementPageResponse;
import com.shop.inventory.internal.service.StockInventoryService;
import com.shop.shared.web.ApiResponse;
import com.shop.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ADMIN_BASE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@Tag(name = "Quản trị tồn kho", description = "Quản lý số lượng tồn theo SKU và địa điểm")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InventoryAdministrationController {

    StockInventoryService inventoryService;

    @GetMapping(STOCK_ITEMS)
    @PreAuthorize("hasAuthority('" + READ + "')")
    @Operation(summary = "Tìm kiếm, sắp xếp và phân trang tồn kho")
    ApiResponse<StockItemPageResponse> search(@Valid @ModelAttribute StockItemSearchRequest request) {
        return ApiResponse.<StockItemPageResponse>builder()
                .result(inventoryService.search(request))
                .build();
    }

    @GetMapping(STOCK_ITEM_BY_ID)
    @PreAuthorize("hasAuthority('" + READ + "')")
    @Operation(summary = "Xem chi tiết số dư tồn kho")
    ApiResponse<StockItemResponse> getById(@PathVariable UUID stockItemId) {
        return ApiResponse.<StockItemResponse>builder()
                .result(inventoryService.getById(stockItemId))
                .build();
    }

    @PostMapping(STOCK_ITEMS)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Khởi tạo tồn kho cho một SKU tại một địa điểm")
    ApiResponse<StockItemResponse> create(@Valid @RequestBody CreateStockItemRequest request) {
        return ApiResponse.<StockItemResponse>builder()
                .result(inventoryService.create(request))
                .build();
    }

    @PostMapping(STOCK_ADJUSTMENTS)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @Operation(summary = "Điều chỉnh số lượng tồn thực tế với optimistic locking")
    ApiResponse<StockItemResponse> adjust(
            @PathVariable UUID stockItemId, @Valid @RequestBody StockAdjustmentRequest request) {
        return ApiResponse.<StockItemResponse>builder()
                .result(inventoryService.adjust(stockItemId, request))
                .build();
    }

    @GetMapping(STOCK_MOVEMENTS)
    @PreAuthorize("hasAuthority('" + READ + "')")
    @Operation(summary = "Xem lịch sử biến động tồn kho theo thứ tự mới nhất")
    ApiResponse<StockMovementPageResponse> getMovements(
            @PathVariable UUID stockItemId, @Valid @ModelAttribute StockMovementSearchRequest request) {
        return ApiResponse.<StockMovementPageResponse>builder()
                .result(inventoryService.getMovements(stockItemId, request))
                .build();
    }
}
