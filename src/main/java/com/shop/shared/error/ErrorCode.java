package com.shop.shared.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Invalid validation key", HttpStatus.BAD_REQUEST),
    USERNAME_ALREADY_EXISTS(1002, "Username already exists", HttpStatus.CONFLICT),
    USERNAME_INVALID(1003, "Username must be between {min} and {max} characters", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Password must be between {min} and {max} characters", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005, "User does not exist", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_DOB(1008, "Date of birth must be in the past", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_EXISTS(1009, "Email already exists", HttpStatus.CONFLICT),
    MALFORMED_REQUEST(1010, "Malformed request", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1011, "Resource not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(1012, "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    IDENTITY_CONFLICT(1013, "Identity data conflicts with an existing account", HttpStatus.CONFLICT),
    INVALID_TOKEN(1014, "Token is invalid or expired", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED(1015, "Refresh token reuse detected", HttpStatus.UNAUTHORIZED),
    TOKEN_TYPE_INVALID(1016, "Token type is invalid", HttpStatus.UNAUTHORIZED),
    USER_DISABLED(1017, "User account is not active", HttpStatus.UNAUTHORIZED),
    INVALID_EMAIL(1018, "Email is invalid", HttpStatus.BAD_REQUEST),
    USERNAME_REQUIRED(1019, "Username is required", HttpStatus.BAD_REQUEST),
    USERNAME_FORMAT_INVALID(
            1020, "Username may contain only letters, numbers, dots, underscores, and hyphens", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(1021, "Password is required", HttpStatus.BAD_REQUEST),
    FIRST_NAME_INVALID(1022, "First name must not exceed {max} characters", HttpStatus.BAD_REQUEST),
    LAST_NAME_INVALID(1023, "Last name must not exceed {max} characters", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(1024, "Too many requests. Please try again later", HttpStatus.TOO_MANY_REQUESTS),
    CAPTCHA_REQUIRED(1025, "Captcha is required", HttpStatus.PRECONDITION_REQUIRED),
    INVALID_CAPTCHA(1026, "Captcha is invalid or expired", HttpStatus.BAD_REQUEST),
    CAPTCHA_UNAVAILABLE(1027, "Captcha is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    CAPTCHA_NOT_REQUIRED(1028, "Captcha is not required", HttpStatus.CONFLICT),
    CURRENT_PASSWORD_INVALID(1029, "Current password is invalid", HttpStatus.BAD_REQUEST),
    PASSWORD_UNCHANGED(1030, "New password must be different from the current password", HttpStatus.BAD_REQUEST),
    REQUIRED_ROLE_MISSING(1500, "Required role is missing", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
