package com.shop.catalog.internal.image.mapper;

import com.shop.catalog.internal.image.dto.response.ProductImageResponse;
import com.shop.catalog.internal.image.entity.ProductImage;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductImageMapper {

    @Mapping(target = "primary", source = "primaryImage")
    ProductImageResponse toResponse(ProductImage image);

    List<ProductImageResponse> toResponses(List<ProductImage> images);
}
