package com.shop.inventory.reservation;

import java.util.UUID;

public interface StockReservationTargetOperations {

    StockReservationTarget resolve(UUID productVariantId, String sku, String locationCode);
}
