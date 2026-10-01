package com.shop.inventory.reservation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record ReserveStockCommand(UUID reservationId, UUID stockItemId, long quantity, Instant expiresAt) {

    public ReserveStockCommand {
        Objects.requireNonNull(reservationId, "reservation id is required");
        Objects.requireNonNull(stockItemId, "stock item id is required");
        Objects.requireNonNull(expiresAt, "expiration is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("reservation quantity must be positive");
        }
        expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }
}
