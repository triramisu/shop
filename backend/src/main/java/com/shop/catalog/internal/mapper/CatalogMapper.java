package com.shop.catalog.internal.mapper;

import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.dto.response.ProductPageResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.dto.response.ProductVariantResponse;
import com.shop.catalog.internal.entity.Category;
import com.shop.catalog.internal.entity.Product;
import com.shop.catalog.internal.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogMapper {

    CategoryResponse toCategoryResponse(Category category);

    ProductResponse toProductResponse(Product product);

    ProductVariantResponse toProductVariantResponse(ProductVariant variant);

    default ProductPageResponse toProductPageResponse(Page<Product> products) {
        return ProductPageResponse.builder()
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
}
