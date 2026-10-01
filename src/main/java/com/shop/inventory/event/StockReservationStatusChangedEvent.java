package com.shop.inventory.event;

import com.shop.inventory.reservation.StockReservationStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockReservationStatusChangedEvent(
        UUID reservationId,
        UUID stockItemId,
        long quantity,
        StockReservationStatus previousStatus,
        StockReservationStatus currentStatus,
        Instant expiresAt,
        Instant occurredAt) {

    public StockReservationStatusChangedEvent {
        Objects.requireNonNull(reservationId, "reservation id is required");
        Objects.requireNonNull(stockItemId, "stock item id is required");
        Objects.requireNonNull(previousStatus, "previous status is required");
        Objects.requireNonNull(currentStatus, "current status is required");
        Objects.requireNonNull(expiresAt, "expiration is required");
        Objects.requireNonNull(occurredAt, "occurred at is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("reservation quantity must be positive");
        }
        if (previousStatus == currentStatus) {
            throw new IllegalArgumentException("reservation status must change");
        }
    }
}
