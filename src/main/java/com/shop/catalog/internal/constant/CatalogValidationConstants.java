package com.shop.catalog.internal.constant;

public final class CatalogValidationConstants {

    public static final int MAX_CATEGORY_CODE_LENGTH = 50;
    public static final int MAX_CATEGORY_NAME_LENGTH = 150;
    public static final int MAX_CATEGORY_SLUG_LENGTH = 180;
    public static final int MAX_PRODUCT_NAME_LENGTH = 200;
    public static final int MAX_PRODUCT_SLUG_LENGTH = 220;
    public static final int MAX_PRODUCT_DESCRIPTION_LENGTH = 5000;
    public static final int MAX_VARIANT_NAME_LENGTH = 200;
    public static final int MAX_SKU_LENGTH = 100;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9_-]*";
    public static final String SKU_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{2,99}";
    public static final String SLUG_PATTERN = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*";
    public static final String CURRENCY_PATTERN = "(?i)[A-Z]{3}";

    private CatalogValidationConstants() {}
}
