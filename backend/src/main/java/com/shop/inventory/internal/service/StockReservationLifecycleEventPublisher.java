package com.shop.inventory.internal.service;

import com.shop.inventory.event.StockReservationStatusChangedEvent;
import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.reservation.StockReservationStatus;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class StockReservationLifecycleEventPublisher {

    ApplicationEventPublisher eventPublisher;

    void publish(StockReservation reservation, StockReservationStatus previousStatus, Instant occurredAt) {
        eventPublisher.publishEvent(new StockReservationStatusChangedEvent(
                reservation.getId(),
                reservation.getStockItem().getId(),
                reservation.getQuantity(),
                previousStatus,
                reservation.getStatus(),
                reservation.getExpiresAt(),
                occurredAt));
    }
}
