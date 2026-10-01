package com.shop.inventory.reservation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockReservationResult(
        UUID reservationId,
        UUID stockItemId,
        long quantity,
        StockReservationStatus status,
        Instant expiresAt,
        long onHand,
        long reserved,
        long available) {

    public StockReservationResult {
        Objects.requireNonNull(reservationId, "reservation id is required");
        Objects.requireNonNull(stockItemId, "stock item id is required");
        Objects.requireNonNull(status, "reservation status is required");
        Objects.requireNonNull(expiresAt, "expiration is required");
        if (quantity <= 0 || onHand < 0 || reserved < 0 || reserved > onHand || available != onHand - reserved) {
            throw new IllegalArgumentException("reservation result contains invalid quantities");
        }
    }
}
