package com.shop.catalog.internal.image.service;

import com.shop.catalog.internal.entity.Product;
import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import com.shop.catalog.internal.image.dto.request.ArrangeProductImagesRequest;
import com.shop.catalog.internal.image.dto.request.UploadProductImagesRequest;
import com.shop.catalog.internal.image.dto.response.ProductImageResponse;
import com.shop.catalog.internal.image.entity.ProductImage;
import com.shop.catalog.internal.image.mapper.ProductImageMapper;
import com.shop.catalog.internal.image.repository.ProductImageRepository;
import com.shop.catalog.internal.image.storage.ObjectStorage;
import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import com.shop.catalog.internal.image.validation.ProductImageValidator;
import com.shop.catalog.internal.image.validation.ValidatedProductImage;
import com.shop.catalog.internal.repository.ProductRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProductImageService {

    ProductRepository productRepository;
    ProductImageRepository imageRepository;
    ProductImageValidator imageValidator;
    ProductImageMapper mapper;
    ObjectStorage objectStorage;
    CatalogImageProperties properties;

    @Transactional(readOnly = true)
    public List<ProductImageResponse> findAll(UUID productId) {
        requireProduct(productId);
        return mapper.toResponses(imageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId));
    }

    @Transactional
    public List<ProductImageResponse> upload(UUID productId, UploadProductImagesRequest request) {
        Product product = lockProduct(productId);
        List<ValidatedProductImage> validatedImages = validateUpload(request);
        List<ProductImage> existingImages = imageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId);
        requireProductLimit(existingImages.size(), validatedImages.size());

        Integer primaryIndex = request.getPrimaryIndex();
        requireValidPrimaryIndex(primaryIndex, validatedImages.size());
        if (primaryIndex != null) {
            existingImages.forEach(image -> image.arrange(false, image.getDisplayOrder()));
        }

        List<String> storedKeys = new ArrayList<>();
        registerRollbackCleanup(storedKeys);
        List<ProductImage> newImages = new ArrayList<>();
        int firstOrder = existingImages.size();

        try {
            for (int index = 0; index < validatedImages.size(); index++) {
                ValidatedProductImage validated = validatedImages.get(index);
                String objectKey = createObjectKey(productId, validated.extension());
                storedKeys.add(objectKey);
                objectStorage.store(new ObjectStorageUpload(objectKey, validated.contentType(), validated.content()));
                boolean primary = primaryIndex != null ? primaryIndex == index : existingImages.isEmpty() && index == 0;
                newImages.add(ProductImage.create(
                        product,
                        objectKey,
                        validated.originalFilename(),
                        validated.contentType(),
                        validated.sizeBytes(),
                        primary,
                        firstOrder + index));
            }
            imageRepository.saveAllAndFlush(newImages);
        } catch (ObjectStorageException exception) {
            throw new AppException(ErrorCode.OBJECT_STORAGE_UNAVAILABLE);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        }

        List<ProductImage> allImages = new ArrayList<>(existingImages);
        allImages.addAll(newImages);
        return mapper.toResponses(allImages);
    }

    @Transactional
    public List<ProductImageResponse> arrange(UUID productId, ArrangeProductImagesRequest request) {
        lockProduct(productId);
        List<ProductImage> images = imageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId);
        requireValidArrangement(images, request);

        var imagesById = images.stream().collect(Collectors.toMap(ProductImage::getId, image -> image));
        for (int index = 0; index < request.getImageIds().size(); index++) {
            UUID imageId = request.getImageIds().get(index);
            imagesById.get(imageId).arrange(imageId.equals(request.getPrimaryImageId()), index);
        }
        try {
            List<ProductImage> savedImages = imageRepository.saveAllAndFlush(images);
            return mapper.toResponses(savedImages.stream()
                    .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                    .toList());
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.CATALOG_CONFLICT);
        }
    }

    @Transactional
    public List<ProductImageResponse> delete(UUID productId, UUID imageId) {
        lockProduct(productId);
        List<ProductImage> images = imageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId);
        ProductImage image = images.stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND));

        images.remove(image);
        imageRepository.delete(image);
        arrangeAfterDeletion(images, image.isPrimaryImage());
        imageRepository.flush();
        registerAfterCommitDeletion(image.getObjectKey());
        return mapper.toResponses(images);
    }

    private List<ValidatedProductImage> validateUpload(UploadProductImagesRequest request) {
        if (request == null || request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_FILES_REQUIRED);
        }
        if (request.getFiles().size() > properties.getMaxFilesPerUpload()) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_COUNT_INVALID);
        }
        return request.getFiles().stream().map(imageValidator::validate).toList();
    }

    private void requireProductLimit(int existingCount, int uploadedCount) {
        if ((long) existingCount + uploadedCount > properties.getMaxFilesPerProduct()) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
        }
    }

    private void requireValidPrimaryIndex(Integer primaryIndex, int imageCount) {
        if (primaryIndex != null && (primaryIndex < 0 || primaryIndex >= imageCount)) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_ARRANGEMENT_INVALID);
        }
    }

    private void requireValidArrangement(List<ProductImage> images, ArrangeProductImagesRequest request) {
        if (images.isEmpty()) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_ARRANGEMENT_INVALID);
        }
        List<UUID> requestedIds = request.getImageIds();
        if (requestedIds == null || request.getPrimaryImageId() == null || requestedIds.size() != images.size()) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_ARRANGEMENT_INVALID);
        }
        Set<UUID> currentIds = images.stream().map(ProductImage::getId).collect(Collectors.toSet());
        Set<UUID> requestedIdSet = new HashSet<>(requestedIds);
        if (requestedIdSet.size() != requestedIds.size()
                || !currentIds.equals(requestedIdSet)
                || !requestedIdSet.contains(request.getPrimaryImageId())) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_ARRANGEMENT_INVALID);
        }
    }

    private void arrangeAfterDeletion(List<ProductImage> remainingImages, boolean primaryDeleted) {
        UUID primaryImageId = primaryDeleted
                ? null
                : remainingImages.stream()
                        .filter(ProductImage::isPrimaryImage)
                        .map(ProductImage::getId)
                        .findFirst()
                        .orElse(null);
        if (primaryImageId == null && !remainingImages.isEmpty()) {
            primaryImageId = remainingImages.getFirst().getId();
        }
        for (int index = 0; index < remainingImages.size(); index++) {
            ProductImage remaining = remainingImages.get(index);
            remaining.arrange(remaining.getId().equals(primaryImageId), index);
        }
    }

    private String createObjectKey(UUID productId, String extension) {
        return "catalog/products/" + productId + "/" + UUID.randomUUID() + "." + extension;
    }

    private Product requireProduct(UUID productId) {
        return productRepository
                .findById(productId)
                .filter(product -> product.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private Product lockProduct(UUID productId) {
        return productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private void registerRollbackCleanup(List<String> storedKeys) {
        requireTransactionSynchronization();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    storedKeys.forEach(ProductImageService.this::deleteObjectQuietly);
                }
            }
        });
    }

    private void registerAfterCommitDeletion(String objectKey) {
        requireTransactionSynchronization();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteObjectQuietly(objectKey);
            }
        });
    }

    private void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Product image operation requires an active transaction");
        }
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (ObjectStorageException exception) {
            log.error("Could not delete catalog object {}", objectKey, exception);
        }
    }
}
