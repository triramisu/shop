package com.shop.catalog.internal.service;

import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.UpdateCategoryRequest;
import com.shop.catalog.internal.dto.request.UpdateCategoryStatusRequest;
import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.entity.Category;
import com.shop.catalog.internal.entity.CategoryStatus;
import com.shop.catalog.internal.mapper.CatalogMapper;
import com.shop.catalog.internal.repository.CategoryRepository;
import com.shop.catalog.internal.repository.ProductRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CatalogCategoryService {

    CategoryRepository categoryRepository;
    ProductRepository productRepository;
    CatalogMapper mapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByDeletedAtIsNullOrderByNameAscIdAsc().stream()
                .map(mapper::toCategoryResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        if (categoryRepository.existsByCodeIgnoreCase(request.getCode())
                || categoryRepository.existsBySlugIgnoreCase(request.getSlug())) {
            throw new AppException(ErrorCode.CATEGORY_IDENTIFIER_ALREADY_EXISTS);
        }

        try {
            Category category = Category.create(request.getCode(), request.getName(), request.getSlug());
            return mapper.toCategoryResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.CATEGORY_IDENTIFIER_ALREADY_EXISTS);
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.CATALOG_DATA_INVALID);
        }
    }

    @Transactional
    public CategoryResponse update(UUID categoryId, UpdateCategoryRequest request) {
        Category category = getCategory(categoryId);
        requireVersion(request.getVersion(), category.getVersion());
        if (categoryRepository.existsBySlugIgnoreCaseAndIdNot(request.getSlug(), categoryId)) {
            throw new AppException(ErrorCode.CATEGORY_IDENTIFIER_ALREADY_EXISTS);
        }

        try {
            category.updateDetails(request.getName(), request.getSlug());
            return mapper.toCategoryResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.CATEGORY_IDENTIFIER_ALREADY_EXISTS);
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new AppException(ErrorCode.CATALOG_STATE_INVALID);
        }
    }

    @Transactional
    public CategoryResponse updateStatus(UUID categoryId, UpdateCategoryStatusRequest request) {
        Category category = getCategory(categoryId);
        requireVersion(request.getVersion(), category.getVersion());

        if (request.getStatus() == CategoryStatus.INACTIVE
                && productRepository.existsByCategoryIdAndDeletedAtIsNull(categoryId)) {
            throw new AppException(ErrorCode.CATEGORY_IN_USE);
        }

        try {
            if (request.getStatus() == CategoryStatus.ACTIVE) {
                category.activate();
            } else {
                category.deactivate();
            }
            return mapper.toCategoryResponse(categoryRepository.saveAndFlush(category));
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        } catch (IllegalStateException exception) {
            throw new AppException(ErrorCode.CATALOG_STATE_INVALID);
        }
    }

    Category getActiveCategory(UUID categoryId) {
        Category category = getCategory(categoryId);
        if (!category.isAvailable()) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private Category getCategory(UUID categoryId) {
        return categoryRepository
                .findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private void requireVersion(long expectedVersion, long actualVersion) {
        if (expectedVersion != actualVersion) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        }
    }
}
