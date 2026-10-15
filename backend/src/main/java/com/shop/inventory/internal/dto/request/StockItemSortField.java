package com.shop.inventory.internal.dto.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StockItemSortField {
    CREATED_AT("createdAt"),
    SKU("sku"),
    LOCATION_CODE("locationCode"),
    ON_HAND("onHand"),
    RESERVED("reserved");

    private final String property;
}
