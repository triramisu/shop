package com.shop.identity.internal.administration.constant;

public final class SystemAdministrationApiPaths {

    public static final String BASE = "/api/system-administration";
    public static final String USERS = "/users";
    public static final String USER_BY_ID = USERS + "/{userId}";
    public static final String USER_STATUS = USER_BY_ID + "/status";
    public static final String USER_ROLES = USER_BY_ID + "/roles";
    public static final String ROLES = "/roles";
    public static final String ROLE_BY_CODE = ROLES + "/{roleCode}";
    public static final String PERMISSIONS = "/permissions";

    private SystemAdministrationApiPaths() {}
}
