package com.shop.catalog.internal.constant;

public final class CatalogApiPaths {

    public static final String BASE = "/api/catalog";
    public static final String CATEGORIES = "/categories";
    public static final String CATEGORY_BY_ID = CATEGORIES + "/{categoryId}";
    public static final String CATEGORY_STATUS = CATEGORY_BY_ID + "/status";
    public static final String PRODUCTS = "/products";
    public static final String PRODUCT_BY_ID = PRODUCTS + "/{productId}";
    public static final String PRODUCT_PUBLISH = PRODUCT_BY_ID + "/publish";
    public static final String PRODUCT_HIDE = PRODUCT_BY_ID + "/hide";
    public static final String PRODUCT_VARIANTS = PRODUCT_BY_ID + "/variants";
    public static final String PRODUCT_VARIANT_BY_ID = PRODUCT_VARIANTS + "/{variantId}";
    public static final String PRODUCT_VARIANT_STATUS = PRODUCT_VARIANT_BY_ID + "/status";

    private CatalogApiPaths() {}
}
