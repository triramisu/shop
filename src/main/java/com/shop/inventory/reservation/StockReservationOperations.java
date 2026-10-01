package com.shop.inventory.reservation;

public interface StockReservationOperations {

    StockReservationResult reserve(ReserveStockCommand command);
}
