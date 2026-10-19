package com.shop.inventory.reservation;

import java.util.Objects;
import java.util.UUID;

public record ConfirmStockReservationCommand(UUID reservationId) {

    public ConfirmStockReservationCommand {
        Objects.requireNonNull(reservationId, "reservation id is required");
    }
}
