package com.shop.catalog.internal.service;

import com.shop.catalog.internal.dto.request.CatalogSortDirection;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.ProductSearchRequest;
import com.shop.catalog.internal.dto.request.ProductSortField;
import com.shop.catalog.internal.dto.request.UpdateProductRequest;
import com.shop.catalog.internal.dto.request.UpdateProductVariantRequest;
import com.shop.catalog.internal.dto.request.UpdateProductVariantStatusRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.dto.response.ProductPageResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.entity.Category;
import com.shop.catalog.internal.entity.Product;
import com.shop.catalog.internal.entity.ProductVariant;
import com.shop.catalog.internal.entity.ProductVariantStatus;
import com.shop.catalog.internal.mapper.CatalogMapper;
import com.shop.catalog.internal.repository.ProductRepository;
import com.shop.catalog.internal.repository.ProductSearchCriteria;
import com.shop.catalog.internal.repository.ProductSearchQuery;
import com.shop.catalog.internal.repository.ProductVariantRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CatalogProductService {

    ProductRepository productRepository;
    ProductSearchQuery productSearchQuery;
    ProductVariantRepository productVariantRepository;
    CatalogCategoryService categoryService;
    CatalogMapper mapper;

    @Transactional(readOnly = true)
    public ProductPageResponse search(ProductSearchRequest request) {
        PageRequest pageRequest = PageRequest.of(
                request.getPage(), request.getSize(), createSort(request.getSortBy(), request.getDirection()));
        ProductSearchCriteria criteria = ProductSearchCriteria.of(
                request.getKeyword(),
                request.getCategoryId(),
                request.getStatus(),
                request.getSortBy(),
                request.getDirection());
        Page<Product> products = productSearchQuery.search(criteria, pageRequest);
        return mapper.toProductPageResponse(products);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID productId) {
        return mapper.toProductResponse(getProduct(productId));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySlugIgnoreCase(request.getSlug())) {
            throw new AppException(ErrorCode.PRODUCT_SLUG_ALREADY_EXISTS);
        }
        Category category = categoryService.getActiveCategory(request.getCategoryId());
        try {
            Product product = Product.create(category, request.getName(), request.getSlug(), request.getDescription());
            return mapper.toProductResponse(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.PRODUCT_SLUG_ALREADY_EXISTS);
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.CATALOG_DATA_INVALID);
        }
    }

    @Transactional
    public ProductResponse update(UUID productId, UpdateProductRequest request) {
        Product product = getProduct(productId);
        requireVersion(request.getVersion(), product.getVersion());
        if (productRepository.existsBySlugIgnoreCaseAndIdNot(request.getSlug(), productId)) {
            throw new AppException(ErrorCode.PRODUCT_SLUG_ALREADY_EXISTS);
        }
        Category category = categoryService.getActiveCategory(request.getCategoryId());

        return saveProduct(
                () -> {
                    product.updateDetails(category, request.getName(), request.getSlug(), request.getDescription());
                    return product;
                },
                ErrorCode.PRODUCT_SLUG_ALREADY_EXISTS);
    }

    @Transactional
    public ProductResponse addVariant(UUID productId, CreateProductVariantRequest request) {
        Product product = getProduct(productId);
        requireVersion(request.getProductVersion(), product.getVersion());
        if (productVariantRepository.existsBySkuIgnoreCase(request.getSku())) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        }

        executeDomainOperation(() ->
                product.addVariant(request.getSku(), request.getName(), request.getPrice(), request.getCurrency()));
        try {
            return mapper.toProductResponse(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        }
    }

    @Transactional
    public ProductResponse updateVariant(UUID productId, UUID variantId, UpdateProductVariantRequest request) {
        Product product = getProduct(productId);
        ProductVariant variant = getVariant(product, variantId);
        requireVersion(request.getVersion(), variant.getVersion());
        executeDomainOperation(
                () -> product.updateVariant(variantId, request.getName(), request.getPrice(), request.getCurrency()));
        try {
            return mapper.toProductResponse(productRepository.saveAndFlush(product));
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        }
    }

    @Transactional
    public ProductResponse updateVariantStatus(
            UUID productId, UUID variantId, UpdateProductVariantStatusRequest request) {
        Product product = getProduct(productId);
        ProductVariant variant = getVariant(product, variantId);
        requireVersion(request.getVersion(), variant.getVersion());
        ProductVariantStatus status =
                ProductVariantStatus.valueOf(request.getStatus().name());
        executeDomainOperation(() -> product.changeVariantStatus(variantId, status));
        try {
            return mapper.toProductResponse(productRepository.saveAndFlush(product));
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        }
    }

    @Transactional
    public ProductResponse publish(UUID productId, VersionedCatalogRequest request) {
        Product product = getProduct(productId);
        requireVersion(request.getVersion(), product.getVersion());
        return saveProduct(() -> {
            product.publish();
            return product;
        });
    }

    @Transactional
    public ProductResponse hide(UUID productId, VersionedCatalogRequest request) {
        Product product = getProduct(productId);
        requireVersion(request.getVersion(), product.getVersion());
        return saveProduct(() -> {
            product.hide();
            return product;
        });
    }

    private ProductResponse saveProduct(Supplier<Product> operation) {
        return saveProduct(operation, ErrorCode.CATALOG_CONFLICT);
    }

    private ProductResponse saveProduct(Supplier<Product> operation, ErrorCode conflictCode) {
        Product changed = executeDomainOperation(operation);
        try {
            return mapper.toProductResponse(productRepository.saveAndFlush(changed));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(conflictCode);
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        }
    }

    private <T> T executeDomainOperation(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.CATALOG_DATA_INVALID);
        } catch (IllegalStateException exception) {
            throw new AppException(ErrorCode.CATALOG_STATE_INVALID);
        }
    }

    private Product getProduct(UUID productId) {
        return productRepository
                .findDetailedById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductVariant getVariant(Product product, UUID variantId) {
        return product.getVariants().stream()
                .filter(variant -> variant.getId().equals(variantId) && variant.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
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

    private void requireVersion(long expectedVersion, long actualVersion) {
        if (expectedVersion != actualVersion) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        }
    }
}
