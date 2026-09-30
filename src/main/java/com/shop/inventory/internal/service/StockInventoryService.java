package com.shop.inventory.internal.service;

import com.shop.catalog.inventory.CatalogSkuLookup;
import com.shop.catalog.inventory.CatalogSkuReference;
import com.shop.inventory.event.StockBalanceChangedEvent;
import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.dto.request.CreateStockItemRequest;
import com.shop.inventory.internal.dto.request.InventorySortDirection;
import com.shop.inventory.internal.dto.request.StockAdjustmentRequest;
import com.shop.inventory.internal.dto.request.StockItemSearchRequest;
import com.shop.inventory.internal.dto.request.StockItemSortField;
import com.shop.inventory.internal.dto.request.StockMovementSearchRequest;
import com.shop.inventory.internal.dto.response.StockItemPageResponse;
import com.shop.inventory.internal.dto.response.StockItemResponse;
import com.shop.inventory.internal.dto.response.StockMovementPageResponse;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockMovement;
import com.shop.inventory.internal.mapper.InventoryMapper;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockMovementRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
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
public class StockInventoryService {

    static final String DEFAULT_INITIAL_REASON = "Khởi tạo tồn kho";

    StockItemRepository stockItemRepository;
    StockMovementRepository stockMovementRepository;
    CatalogSkuLookup catalogSkuLookup;
    InventoryMapper mapper;
    ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public StockItemPageResponse search(StockItemSearchRequest request) {
        PageRequest pageRequest = PageRequest.of(
                request.getPage(), request.getSize(), createSort(request.getSortBy(), request.getDirection()));
        Page<StockItem> stockItems = stockItemRepository.search(
                likePattern(request.getKeyword()), normalizedLocation(request.getLocationCode()), pageRequest);
        return mapper.toStockItemPageResponse(stockItems);
    }

    @Transactional(readOnly = true)
    public StockItemResponse getById(UUID stockItemId) {
        return mapper.toStockItemResponse(getStockItem(stockItemId));
    }

    @Transactional(readOnly = true)
    public StockMovementPageResponse getMovements(UUID stockItemId, StockMovementSearchRequest request) {
        getStockItem(stockItemId);
        PageRequest pageRequest = PageRequest.of(
                request.getPage(), request.getSize(), Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        return mapper.toStockMovementPageResponse(stockMovementRepository.findByStockItemId(stockItemId, pageRequest));
    }

    @Transactional
    public StockItemResponse create(CreateStockItemRequest request) {
        CatalogSkuReference skuReference = catalogSkuLookup
                .findBySku(request.getSku())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_SKU_NOT_FOUND));
        String locationCode = normalizedLocation(request.getLocationCode());
        if (stockItemRepository.existsByProductVariantIdAndLocationCodeIgnoreCase(
                skuReference.productVariantId(), locationCode)) {
            throw new AppException(ErrorCode.STOCK_ITEM_ALREADY_EXISTS);
        }

        try {
            StockItem stockItem = StockItem.create(
                    skuReference.productVariantId(), skuReference.sku(), locationCode, request.getInitialQuantity());
            StockItem saved = stockItemRepository.saveAndFlush(stockItem);
            if (request.getInitialQuantity() > 0) {
                recordMovement(
                        saved,
                        StockMovementType.INITIAL,
                        request.getInitialQuantity(),
                        0,
                        defaultInitialReason(request.getReason()),
                        request.getReferenceId());
            }
            return mapper.toStockItemResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.STOCK_ITEM_ALREADY_EXISTS);
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVENTORY_DATA_INVALID);
        }
    }

    @Transactional
    public StockItemResponse adjust(UUID stockItemId, StockAdjustmentRequest request) {
        StockItem stockItem = getStockItem(stockItemId);
        if (stockItem.getVersion() != request.getVersion()) {
            throw new AppException(ErrorCode.INVENTORY_CONFLICT);
        }
        try {
            stockItem.adjustOnHand(request.getQuantityDelta());
            StockItem saved = stockItemRepository.saveAndFlush(stockItem);
            recordMovement(
                    saved,
                    StockMovementType.ADJUSTMENT,
                    request.getQuantityDelta(),
                    0,
                    request.getReason(),
                    request.getReferenceId());
            return mapper.toStockItemResponse(saved);
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.INVENTORY_CONFLICT);
        } catch (DataIntegrityViolationException | IllegalStateException exception) {
            throw new AppException(ErrorCode.INVENTORY_BALANCE_INVALID);
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVENTORY_QUANTITY_INVALID);
        }
    }

    private void recordMovement(
            StockItem stockItem,
            StockMovementType type,
            long onHandDelta,
            long reservedDelta,
            String reason,
            String referenceId) {
        Instant occurredAt = Instant.now();
        StockMovement movement =
                StockMovement.record(stockItem, type, onHandDelta, reservedDelta, reason, referenceId, occurredAt);
        stockMovementRepository.saveAndFlush(movement);
        eventPublisher.publishEvent(new StockBalanceChangedEvent(
                stockItem.getId(),
                stockItem.getProductVariantId(),
                stockItem.getSku(),
                stockItem.getLocationCode(),
                stockItem.getOnHand(),
                stockItem.getReserved(),
                stockItem.getAvailable(),
                type,
                movement.getReferenceId(),
                occurredAt));
    }

    private StockItem getStockItem(UUID stockItemId) {
        return stockItemRepository
                .findById(stockItemId)
                .orElseThrow(() -> new AppException(ErrorCode.STOCK_ITEM_NOT_FOUND));
    }

    private Sort createSort(StockItemSortField field, InventorySortDirection direction) {
        Sort.Direction springDirection =
                direction == InventorySortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order requested = new Sort.Order(springDirection, field.getProperty());
        if (field == StockItemSortField.SKU || field == StockItemSortField.LOCATION_CODE) {
            requested = requested.ignoreCase();
        }
        return Sort.by(requested, Sort.Order.asc("id"));
    }

    private String normalizedLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private String defaultInitialReason(String reason) {
        return reason == null || reason.isBlank() ? DEFAULT_INITIAL_REASON : reason;
    }
}
