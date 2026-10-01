package com.shop.inventory.internal.service;

import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.reservation.StockReservationTarget;
import com.shop.inventory.reservation.StockReservationTargetOperations;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockReservationTargetQueryService implements StockReservationTargetOperations {

    StockItemRepository stockItemRepository;

    @Override
    @Transactional(readOnly = true)
    public StockReservationTarget resolve(UUID productVariantId, String sku, String locationCode) {
        UUID variantId = Objects.requireNonNull(productVariantId, "product variant id is required");
        String normalizedSku = normalize(sku, "sku").toUpperCase(Locale.ROOT);
        String normalizedLocation = normalize(locationCode, "location code").toUpperCase(Locale.ROOT);
        StockItem stockItem = stockItemRepository
                .findByProductVariantIdAndLocationCodeIgnoreCase(variantId, normalizedLocation)
                .filter(item -> item.getSku().equalsIgnoreCase(normalizedSku))
                .orElseThrow(() -> new AppException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        return new StockReservationTarget(
                stockItem.getId(), stockItem.getProductVariantId(), stockItem.getSku(), stockItem.getLocationCode());
    }

    private String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
