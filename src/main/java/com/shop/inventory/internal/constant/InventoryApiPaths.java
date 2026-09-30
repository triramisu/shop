package com.shop.inventory.internal.constant;

public final class InventoryApiPaths {

    public static final String ADMIN_BASE = "/api/admin/inventory";
    public static final String STOCK_ITEMS = "/stock-items";
    public static final String STOCK_ITEM_BY_ID = STOCK_ITEMS + "/{stockItemId}";
    public static final String STOCK_ADJUSTMENTS = STOCK_ITEM_BY_ID + "/adjustments";
    public static final String STOCK_MOVEMENTS = STOCK_ITEM_BY_ID + "/movements";

    private InventoryApiPaths() {}
}
