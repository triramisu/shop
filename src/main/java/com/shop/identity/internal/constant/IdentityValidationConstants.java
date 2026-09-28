package com.shop.identity.internal.constant;

public final class IdentityValidationConstants {

    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 50;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final int MAX_TOKEN_LENGTH = 8192;
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]+$";

    private IdentityValidationConstants() {}
}
