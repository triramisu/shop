package com.shop.catalog.internal.storefront.mapper;

import com.shop.catalog.internal.entity.Category;
import com.shop.catalog.internal.entity.Product;
import com.shop.catalog.internal.entity.ProductVariant;
import com.shop.catalog.internal.image.entity.ProductImage;
import com.shop.catalog.internal.storefront.dto.response.StorefrontCategoryResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductImageResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductPageResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductVariantResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class StorefrontCatalogMapper {

    public StorefrontCategoryResponse toCategoryResponse(Category category) {
        return StorefrontCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .build();
    }

    public StorefrontProductResponse toProductResponse(Product product) {
        return StorefrontProductResponse.builder()
                .id(product.getId())
                .category(toCategoryResponse(product.getCategory()))
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .variants(product.getVariants().stream()
                        .filter(ProductVariant::isSellable)
                        .map(this::toVariantResponse)
                        .toList())
                .build();
    }

    public StorefrontProductPageResponse toProductPageResponse(Page<Product> products) {
        return StorefrontProductPageResponse.builder()
                .content(products.getContent().stream()
                        .map(this::toProductResponse)
                        .toList())
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .first(products.isFirst())
                .last(products.isLast())
                .build();
    }

    public StorefrontProductImageResponse toImageResponse(ProductImage image) {
        return StorefrontProductImageResponse.builder()
                .id(image.getId())
                .contentType(image.getContentType())
                .primary(image.isPrimaryImage())
                .displayOrder(image.getDisplayOrder())
                .build();
    }

    private StorefrontProductVariantResponse toVariantResponse(ProductVariant variant) {
        return StorefrontProductVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .name(variant.getName())
                .price(variant.getPrice())
                .currency(variant.getCurrency())
                .build();
    }
}
