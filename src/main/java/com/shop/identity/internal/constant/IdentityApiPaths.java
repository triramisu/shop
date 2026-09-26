package com.shop.identity.internal.constant;

public final class IdentityApiPaths {

    public static final String AUTH_BASE = "/api/auth";
    public static final String AUTH_PATTERN = AUTH_BASE + "/**";

    public static final String REGISTER_SEGMENT = "/register";
    public static final String TOKEN_SEGMENT = "/token";
    public static final String CAPTCHA_SEGMENT = "/captcha";
    public static final String INTROSPECT_SEGMENT = "/introspect";
    public static final String REFRESH_SEGMENT = "/refresh";
    public static final String LOGOUT_SEGMENT = "/logout";

    public static final String REGISTER = AUTH_BASE + REGISTER_SEGMENT;
    public static final String TOKEN = AUTH_BASE + TOKEN_SEGMENT;
    public static final String CAPTCHA = AUTH_BASE + CAPTCHA_SEGMENT;
    public static final String INTROSPECT = AUTH_BASE + INTROSPECT_SEGMENT;
    public static final String REFRESH = AUTH_BASE + REFRESH_SEGMENT;
    public static final String LOGOUT = AUTH_BASE + LOGOUT_SEGMENT;

    private IdentityApiPaths() {}
}
