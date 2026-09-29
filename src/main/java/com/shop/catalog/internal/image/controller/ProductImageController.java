package com.shop.catalog.internal.image.controller;

import static com.shop.catalog.internal.constant.CatalogApiPaths.BASE;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_IMAGES;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_IMAGE_ARRANGEMENT;
import static com.shop.catalog.internal.constant.CatalogApiPaths.PRODUCT_IMAGE_BY_ID;

import com.shop.catalog.internal.image.dto.request.ArrangeProductImagesRequest;
import com.shop.catalog.internal.image.dto.request.UploadProductImagesRequest;
import com.shop.catalog.internal.image.dto.response.ProductImageResponse;
import com.shop.catalog.internal.image.service.ProductImageService;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(BASE)
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
@Tag(name = "Quản trị ảnh sản phẩm", description = "Tải lên, sắp xếp và xóa ảnh của sản phẩm")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductImageController {

    ProductImageService imageService;

    @GetMapping(PRODUCT_IMAGES)
    @Operation(summary = "Xem danh sách ảnh của sản phẩm")
    ApiResponse<List<ProductImageResponse>> getImages(@PathVariable UUID productId) {
        return ApiResponse.<List<ProductImageResponse>>builder()
                .result(imageService.findAll(productId))
                .build();
    }

    @PostMapping(value = PRODUCT_IMAGES, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải nhiều ảnh JPEG hoặc PNG cho sản phẩm")
    ApiResponse<List<ProductImageResponse>> uploadImages(
            @PathVariable UUID productId, @Valid @ModelAttribute UploadProductImagesRequest request) {
        return ApiResponse.<List<ProductImageResponse>>builder()
                .result(imageService.upload(productId, request))
                .build();
    }

    @PutMapping(PRODUCT_IMAGE_ARRANGEMENT)
    @Operation(summary = "Sắp xếp ảnh và chọn ảnh đại diện")
    ApiResponse<List<ProductImageResponse>> arrangeImages(
            @PathVariable UUID productId, @Valid @RequestBody ArrangeProductImagesRequest request) {
        return ApiResponse.<List<ProductImageResponse>>builder()
                .result(imageService.arrange(productId, request))
                .build();
    }

    @DeleteMapping(PRODUCT_IMAGE_BY_ID)
    @Operation(summary = "Xóa ảnh và sắp xếp lại các ảnh còn lại")
    ApiResponse<List<ProductImageResponse>> deleteImage(@PathVariable UUID productId, @PathVariable UUID imageId) {
        return ApiResponse.<List<ProductImageResponse>>builder()
                .result(imageService.delete(productId, imageId))
                .build();
    }
}
