package com.shop.inventory.reservation;

public interface StockReservationOperations {

    StockReservationResult reserve(ReserveStockCommand command);

    StockReservationResult confirm(ConfirmStockReservationCommand command);

    StockReservationResult release(ReleaseStockReservationCommand command);
}
