package com.shop.inventory.reservation;

import java.util.Objects;
import java.util.UUID;

public record ReleaseStockReservationCommand(UUID reservationId) {

    public ReleaseStockReservationCommand {
        Objects.requireNonNull(reservationId, "reservation id is required");
    }
}
