package com.shop.catalog.internal.storefront.controller;

import static com.shop.catalog.internal.constant.CatalogApiPaths.CATEGORIES;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCTS;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_BY_ID;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_IMAGES;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PUBLIC_BASE;

import com.shop.catalog.internal.storefront.dto.request.StorefrontProductSearchRequest;
import com.shop.catalog.internal.storefront.dto.response.StorefrontCategoryResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductImageResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductPageResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductResponse;
import com.shop.catalog.internal.storefront.service.StorefrontCatalogService;
import com.shop.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PUBLIC_BASE)
@Tag(name = "Catalog cửa hàng", description = "API công khai chỉ đọc sản phẩm đã công bố")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorefrontCatalogController {

    StorefrontCatalogService storefrontService;

    @GetMapping(CATEGORIES)
    @Operation(summary = "Xem danh mục đang hoạt động")
    ApiResponse<List<StorefrontCategoryResponse>> getCategories() {
        return ApiResponse.<List<StorefrontCategoryResponse>>builder()
                .result(storefrontService.findCategories())
                .build();
    }

    @GetMapping(PRODUCTS)
    @Operation(summary = "Tìm kiếm và phân trang sản phẩm đã công bố")
    ApiResponse<StorefrontProductPageResponse> searchProducts(
            @Valid @ModelAttribute StorefrontProductSearchRequest request) {
        return ApiResponse.<StorefrontProductPageResponse>builder()
                .result(storefrontService.search(request))
                .build();
    }

    @GetMapping(PRODUCT_BY_ID)
    @Operation(summary = "Xem chi tiết sản phẩm đã công bố")
    ApiResponse<StorefrontProductResponse> getProduct(@PathVariable UUID productId) {
        return ApiResponse.<StorefrontProductResponse>builder()
                .result(storefrontService.getProduct(productId))
                .build();
    }

    @GetMapping(PRODUCT_IMAGES)
    @Operation(summary = "Xem ảnh của sản phẩm đã công bố")
    ApiResponse<List<StorefrontProductImageResponse>> getProductImages(@PathVariable UUID productId) {
        return ApiResponse.<List<StorefrontProductImageResponse>>builder()
                .result(storefrontService.findImages(productId))
                .build();
    }
}
