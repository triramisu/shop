package com.shop.catalog.internal.controller;

import static com.shop.catalog.internal.constant.CatalogApiPaths.ADMIN_BASE;
import static com.shop.catalog.internal.constant.CatalogApiPaths.CATEGORIES;
import static com.shop.catalog.internal.constant.CatalogApiPaths.CATEGORY_BY_ID;
import static com.shop.catalog.internal.constant.CatalogApiPaths.CATEGORY_STATUS;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCTS;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_BY_ID;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_HIDE;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_PUBLISH;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_VARIANTS;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_VARIANT_BY_ID;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_VARIANT_STATUS;
import static com.shop.catalog.internal.constant.CatalogAuthority.PUBLISH;
import static com.shop.catalog.internal.constant.CatalogAuthority.READ;
import static com.shop.catalog.internal.constant.CatalogAuthority.WRITE;

import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.ProductSearchRequest;
import com.shop.catalog.internal.dto.request.UpdateCategoryRequest;
import com.shop.catalog.internal.dto.request.UpdateCategoryStatusRequest;
import com.shop.catalog.internal.dto.request.UpdateProductRequest;
import com.shop.catalog.internal.dto.request.UpdateProductVariantRequest;
import com.shop.catalog.internal.dto.request.UpdateProductVariantStatusRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.dto.response.ProductPageResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.shared.web.ApiResponse;
import com.shop.shared.web.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ADMIN_BASE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@Tag(name = "Quản trị Catalog", description = "Quản lý danh mục, sản phẩm và biến thể bán hàng")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CatalogAdministrationController {

    CatalogCategoryService categoryService;
    CatalogProductService productService;

    @GetMapping(CATEGORIES)
    @PreAuthorize("hasAuthority('" + READ + "')")
    @Operation(summary = "Xem danh sách danh mục đang tồn tại")
    ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.<List<CategoryResponse>>builder()
                .result(categoryService.findAll())
                .build();
    }

    @PostMapping(CATEGORIES)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo danh mục")
    ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.create(request))
                .build();
    }

    @PutMapping(CATEGORY_BY_ID)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @Operation(summary = "Cập nhật danh mục với optimistic locking")
    ApiResponse<CategoryResponse> updateCategory(
            @PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.update(categoryId, request))
                .build();
    }

    @PatchMapping(CATEGORY_STATUS)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @Operation(summary = "Bật hoặc tắt danh mục")
    ApiResponse<CategoryResponse> updateCategoryStatus(
            @PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryStatusRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.updateStatus(categoryId, request))
                .build();
    }

    @GetMapping(PRODUCTS)
    @PreAuthorize("hasAuthority('" + READ + "')")
    @Operation(summary = "Tìm kiếm, sắp xếp và phân trang sản phẩm")
    ApiResponse<ProductPageResponse> searchProducts(@Valid @ModelAttribute ProductSearchRequest request) {
        return ApiResponse.<ProductPageResponse>builder()
                .result(productService.search(request))
                .build();
    }

    @GetMapping(PRODUCT_BY_ID)
    @PreAuthorize("hasAuthority('" + READ + "')")
    @Operation(summary = "Xem chi tiết sản phẩm và biến thể")
    ApiResponse<ProductResponse> getProduct(@PathVariable UUID productId) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.getById(productId))
                .build();
    }

    @PostMapping(PRODUCTS)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo sản phẩm nháp")
    ApiResponse<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.create(request))
                .build();
    }

    @PutMapping(PRODUCT_BY_ID)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @Operation(summary = "Cập nhật sản phẩm với optimistic locking")
    ApiResponse<ProductResponse> updateProduct(
            @PathVariable UUID productId, @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.update(productId, request))
                .build();
    }

    @PatchMapping(PRODUCT_PUBLISH)
    @PreAuthorize("hasAuthority('" + PUBLISH + "')")
    @Operation(summary = "Công bố sản phẩm có ít nhất một biến thể hoạt động")
    ApiResponse<ProductResponse> publishProduct(
            @PathVariable UUID productId, @Valid @RequestBody VersionedCatalogRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.publish(productId, request))
                .build();
    }

    @PatchMapping(PRODUCT_HIDE)
    @PreAuthorize("hasAuthority('" + PUBLISH + "')")
    @Operation(summary = "Ẩn sản phẩm đã công bố")
    ApiResponse<ProductResponse> hideProduct(
            @PathVariable UUID productId, @Valid @RequestBody VersionedCatalogRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.hide(productId, request))
                .build();
    }

    @PostMapping(PRODUCT_VARIANTS)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm biến thể SKU vào sản phẩm")
    ApiResponse<ProductResponse> addVariant(
            @PathVariable UUID productId, @Valid @RequestBody CreateProductVariantRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.addVariant(productId, request))
                .build();
    }

    @PutMapping(PRODUCT_VARIANT_BY_ID)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @Operation(summary = "Cập nhật tên và giá của biến thể")
    ApiResponse<ProductResponse> updateVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateProductVariantRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.updateVariant(productId, variantId, request))
                .build();
    }

    @PatchMapping(PRODUCT_VARIANT_STATUS)
    @PreAuthorize("hasAuthority('" + WRITE + "')")
    @Operation(summary = "Bật hoặc tắt biến thể")
    ApiResponse<ProductResponse> updateVariantStatus(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateProductVariantStatusRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.updateVariantStatus(productId, variantId, request))
                .build();
    }
}
