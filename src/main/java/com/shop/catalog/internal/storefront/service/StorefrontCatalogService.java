package com.shop.catalog.internal.storefront.service;

import com.shop.catalog.internal.dto.request.CatalogSortDirection;
import com.shop.catalog.internal.dto.request.ProductSortField;
import com.shop.catalog.internal.entity.CategoryStatus;
import com.shop.catalog.internal.entity.Product;
import com.shop.catalog.internal.entity.ProductStatus;
import com.shop.catalog.internal.image.entity.ProductImage;
import com.shop.catalog.internal.image.repository.ProductImageRepository;
import com.shop.catalog.internal.image.storage.ObjectStorage;
import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.repository.CategoryRepository;
import com.shop.catalog.internal.repository.ProductRepository;
import com.shop.catalog.internal.repository.ProductSearchCriteria;
import com.shop.catalog.internal.repository.ProductSearchQuery;
import com.shop.catalog.internal.storefront.dto.request.StorefrontProductSearchRequest;
import com.shop.catalog.internal.storefront.dto.response.StorefrontCategoryResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductImageResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductPageResponse;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductResponse;
import com.shop.catalog.internal.storefront.mapper.StorefrontCatalogMapper;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorefrontCatalogService {

    CategoryRepository categoryRepository;
    ProductRepository productRepository;
    ProductSearchQuery productSearchQuery;
    ProductImageRepository imageRepository;
    ObjectStorage objectStorage;
    StorefrontCatalogMapper mapper;

    @Transactional(readOnly = true)
    public List<StorefrontCategoryResponse> findCategories() {
        return categoryRepository.findAllByStatusAndDeletedAtIsNullOrderByNameAscIdAsc(CategoryStatus.ACTIVE).stream()
                .map(mapper::toCategoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StorefrontProductPageResponse search(StorefrontProductSearchRequest request) {
        PageRequest pageRequest = PageRequest.of(
                request.getPage(), request.getSize(), createSort(request.getSortBy(), request.getDirection()));
        ProductSearchCriteria criteria = ProductSearchCriteria.of(
                request.getKeyword(),
                request.getCategoryId(),
                ProductStatus.PUBLISHED,
                request.getSortBy(),
                request.getDirection());
        Page<Product> products = productSearchQuery.search(criteria, pageRequest);
        return mapper.toProductPageResponse(products);
    }

    @Transactional(readOnly = true)
    public StorefrontProductResponse getProduct(UUID productId) {
        return mapper.toProductResponse(getPublishedProduct(productId));
    }

    @Transactional(readOnly = true)
    public List<StorefrontProductImageResponse> findImages(UUID productId) {
        getPublishedProduct(productId);
        try {
            return imageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId).stream()
                    .map(this::toImageResponse)
                    .toList();
        } catch (ObjectStorageException exception) {
            throw new AppException(ErrorCode.OBJECT_STORAGE_UNAVAILABLE);
        }
    }

    private StorefrontProductImageResponse toImageResponse(ProductImage image) {
        StorefrontProductImageResponse response = mapper.toImageResponse(image);
        objectStorage.createReadUrl(image.getObjectKey()).ifPresent(response::setUrl);
        return response;
    }

    private Product getPublishedProduct(UUID productId) {
        return productRepository
                .findStorefrontById(productId, ProductStatus.PUBLISHED, CategoryStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private Sort createSort(ProductSortField sortField, CatalogSortDirection direction) {
        Sort.Direction springDirection =
                direction == CatalogSortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order requestedOrder = new Sort.Order(springDirection, sortField.getProperty());
        if (sortField == ProductSortField.NAME) {
            requestedOrder = requestedOrder.ignoreCase();
        }
        return Sort.by(requestedOrder, Sort.Order.asc("id"));
    }
}
