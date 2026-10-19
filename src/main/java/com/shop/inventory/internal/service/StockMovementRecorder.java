package com.shop.inventory.internal.service;

import com.shop.inventory.event.StockBalanceChangedEvent;
import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockMovement;
import com.shop.inventory.internal.repository.StockMovementRepository;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class StockMovementRecorder {

    StockMovementRepository stockMovementRepository;
    ApplicationEventPublisher eventPublisher;

    void record(
            StockItem stockItem,
            StockMovementType type,
            long onHandDelta,
            long reservedDelta,
            String reason,
            String referenceId,
            Instant occurredAt) {
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
}
