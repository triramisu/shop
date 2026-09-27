package com.shop.identity.internal.constant;

public final class IdentityTableNames {

    public static final String USERS = "xac_thuc_nguoi_dung";
    public static final String ROLES = "xac_thuc_vai_tro";
    public static final String PERMISSIONS = "xac_thuc_quyen_han";
    public static final String USER_ROLES = "xac_thuc_nguoi_dung_vai_tro";
    public static final String ROLE_PERMISSIONS = "xac_thuc_vai_tro_quyen_han";
    public static final String REFRESH_SESSIONS = "xac_thuc_phien_lam_moi";
    public static final String LOGIN_FAILURES = "xac_thuc_dang_nhap_that_bai";
    public static final String CAPTCHA_CHALLENGES = "xac_thuc_thu_thach_captcha";
    public static final String CAPTCHA_LOCKS = "xac_thuc_khoa_captcha";

    private IdentityTableNames() {}
}
