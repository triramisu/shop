package com.shop.order.internal.constant;

public final class OrderApiPaths {

    public static final String CART_BASE = "/api/cart";
    public static final String CHECKOUT_BASE = "/api/checkout";
    public static final String ITEMS = "/items";
    public static final String ITEM_BY_ID = ITEMS + "/{itemId}";
    public static final String QUOTE = "/quote";

    private OrderApiPaths() {}
}
