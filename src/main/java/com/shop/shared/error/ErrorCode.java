package com.shop.shared.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "error.uncategorized", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "error.validation-key.invalid", HttpStatus.BAD_REQUEST),
    USERNAME_ALREADY_EXISTS(1002, "error.username.exists", HttpStatus.CONFLICT),
    USERNAME_INVALID(1003, "error.username.length", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "error.password.length", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005, "error.user.not-found", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "error.authentication.required", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "error.permission.denied", HttpStatus.FORBIDDEN),
    INVALID_DOB(1008, "error.date-of-birth.invalid", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_EXISTS(1009, "error.email.exists", HttpStatus.CONFLICT),
    MALFORMED_REQUEST(1010, "error.request.malformed", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1011, "error.resource.not-found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(1012, "error.method.not-allowed", HttpStatus.METHOD_NOT_ALLOWED),
    IDENTITY_CONFLICT(1013, "error.identity.conflict", HttpStatus.CONFLICT),
    INVALID_TOKEN(1014, "error.token.invalid", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED(1015, "error.refresh-token.reused", HttpStatus.UNAUTHORIZED),
    TOKEN_TYPE_INVALID(1016, "error.token.type-invalid", HttpStatus.UNAUTHORIZED),
    USER_DISABLED(1017, "error.user.inactive", HttpStatus.UNAUTHORIZED),
    INVALID_EMAIL(1018, "error.email.invalid", HttpStatus.BAD_REQUEST),
    USERNAME_REQUIRED(1019, "error.username.required", HttpStatus.BAD_REQUEST),
    USERNAME_FORMAT_INVALID(1020, "error.username.format", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(1021, "error.password.required", HttpStatus.BAD_REQUEST),
    FIRST_NAME_INVALID(1022, "error.first-name.length", HttpStatus.BAD_REQUEST),
    LAST_NAME_INVALID(1023, "error.last-name.length", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(1024, "error.rate-limit.exceeded", HttpStatus.TOO_MANY_REQUESTS),
    CAPTCHA_REQUIRED(1025, "error.captcha.required", HttpStatus.PRECONDITION_REQUIRED),
    INVALID_CAPTCHA(1026, "error.captcha.invalid", HttpStatus.BAD_REQUEST),
    CAPTCHA_UNAVAILABLE(1027, "error.captcha.unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    CAPTCHA_NOT_REQUIRED(1028, "error.captcha.not-required", HttpStatus.CONFLICT),
    CURRENT_PASSWORD_INVALID(1029, "error.password.current-invalid", HttpStatus.BAD_REQUEST),
    PASSWORD_UNCHANGED(1030, "error.password.unchanged", HttpStatus.BAD_REQUEST),
    ROLE_CODE_REQUIRED(1031, "error.role-code.required", HttpStatus.BAD_REQUEST),
    ROLE_CODE_INVALID(1032, "error.role-code.invalid", HttpStatus.BAD_REQUEST),
    ROLE_DESCRIPTION_INVALID(1033, "error.role-description.length", HttpStatus.BAD_REQUEST),
    ROLE_SET_REQUIRED(1034, "error.role-set.required", HttpStatus.BAD_REQUEST),
    USER_STATUS_REQUIRED(1035, "error.user-status.required", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1036, "error.managed-user.not-found", HttpStatus.NOT_FOUND),
    ROLE_NOT_FOUND(1037, "error.role.not-found", HttpStatus.NOT_FOUND),
    PERMISSION_NOT_FOUND(1038, "error.permission.not-found", HttpStatus.NOT_FOUND),
    ROLE_ALREADY_EXISTS(1039, "error.role.exists", HttpStatus.CONFLICT),
    SYSTEM_ROLE_PROTECTED(1040, "error.system-role.protected", HttpStatus.CONFLICT),
    ROLE_IN_USE(1041, "error.role.in-use", HttpStatus.CONFLICT),
    ADMIN_PROTECTED(1042, "error.admin.protected", HttpStatus.CONFLICT),
    ADMIN_ASSIGNMENT_FORBIDDEN(1043, "error.admin.assignment-forbidden", HttpStatus.FORBIDDEN),
    PROTECTED_ROLE_ASSIGNMENT_FORBIDDEN(1044, "error.protected-role.assignment-forbidden", HttpStatus.FORBIDDEN),
    SELF_LOCK_FORBIDDEN(1045, "error.self-lock.forbidden", HttpStatus.CONFLICT),
    ADMINISTRATION_CONFLICT(1046, "error.administration.conflict", HttpStatus.CONFLICT),
    SEARCH_KEYWORD_INVALID(1047, "error.search-keyword.length", HttpStatus.BAD_REQUEST),
    PAGE_NUMBER_INVALID(1048, "error.page-number.invalid", HttpStatus.BAD_REQUEST),
    PAGE_SIZE_INVALID(1049, "error.page-size.invalid", HttpStatus.BAD_REQUEST),
    PERMISSION_CODE_REQUIRED(1050, "error.permission-code.required", HttpStatus.BAD_REQUEST),
    PERMISSION_CODE_INVALID(1051, "error.permission-code.invalid", HttpStatus.BAD_REQUEST),
    ROLE_SET_INVALID(1052, "error.role-set.invalid", HttpStatus.BAD_REQUEST),
    PERMISSION_SET_REQUIRED(1053, "error.permission-set.required", HttpStatus.BAD_REQUEST),
    PERMISSION_SET_INVALID(1054, "error.permission-set.invalid", HttpStatus.BAD_REQUEST),
    PROTECTED_PERMISSION_ASSIGNMENT_FORBIDDEN(
            1055, "error.protected-permission.assignment-forbidden", HttpStatus.FORBIDDEN),
    REQUIRED_ROLE_MISSING(1500, "error.required-role.missing", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String messageKey;
    private final HttpStatusCode statusCode;
}
