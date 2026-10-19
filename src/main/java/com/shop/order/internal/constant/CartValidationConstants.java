package com.shop.order.internal.constant;

public final class CartValidationConstants {

    public static final int MAX_SKU_LENGTH = 100;
    public static final String SKU_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{2,99}";
    public static final int MIN_ITEM_QUANTITY = 1;
    public static final int MAX_ITEM_QUANTITY = 99;
    public static final int MAX_DISTINCT_ITEMS = 100;

    private CartValidationConstants() {}
}
