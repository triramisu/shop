ALTER TABLE identity_role_permissions
    RENAME TO xac_thuc_vai_tro_quyen_han;

ALTER TABLE identity_user_roles
    RENAME TO xac_thuc_nguoi_dung_vai_tro;

ALTER TABLE identity_refresh_tokens
    RENAME TO xac_thuc_phien_lam_moi;

ALTER TABLE identity_permissions
    RENAME TO xac_thuc_quyen_han;

ALTER TABLE identity_roles
    RENAME TO xac_thuc_vai_tro;

ALTER TABLE identity_users
    RENAME TO xac_thuc_nguoi_dung;

ALTER TABLE identity_login_failures
    RENAME TO xac_thuc_dang_nhap_that_bai;

ALTER TABLE identity_captcha_challenges
    RENAME TO xac_thuc_thu_thach_captcha;

ALTER TABLE identity_captcha_store_locks
    RENAME TO xac_thuc_khoa_captcha;
