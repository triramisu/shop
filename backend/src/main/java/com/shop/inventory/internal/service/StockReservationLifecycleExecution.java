package com.shop.inventory.internal.service;

import com.shop.inventory.reservation.StockReservationResult;

record StockReservationLifecycleExecution(StockReservationResult result, Outcome outcome) {

    enum Outcome {
        CONFIRMED,
        EXPIRED_DURING_CONFIRM
    }
}
