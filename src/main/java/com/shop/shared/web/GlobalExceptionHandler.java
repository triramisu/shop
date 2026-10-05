package com.shop.shared.web;

import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import com.shop.shared.error.ErrorMessageResolver;
import jakarta.validation.ConstraintViolation;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class GlobalExceptionHandler {

    private static final String MIN_ATTRIBUTE = "min";
    private static final String MAX_ATTRIBUTE = "max";

    ErrorMessageResolver messageResolver;

    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception) {
        return errorResponse(exception.getErrorCode());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        return errorResponse(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        ErrorCode errorCode = resolveValidationError(fieldError);
        String message = messageResolver.resolve(errorCode);

        if (fieldError != null) {
            try {
                ConstraintViolation<?> violation = fieldError.unwrap(ConstraintViolation.class);
                message = mapAttributes(
                        message, violation.getConstraintDescriptor().getAttributes());
            } catch (RuntimeException ignored) {
                log.debug("Validation metadata is unavailable for field {}", fieldError.getField());
            }
        }

        return errorResponse(errorCode, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleMalformedRequest(HttpMessageNotReadableException exception) {
        return errorResponse(ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return errorResponse(ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return errorResponse(ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
        return errorResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return errorResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return errorResponse(ErrorCode.PRODUCT_IMAGE_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled request failure", exception);
        return errorResponse(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    private ErrorCode resolveValidationError(FieldError fieldError) {
        if (fieldError == null || fieldError.getDefaultMessage() == null) {
            return ErrorCode.INVALID_KEY;
        }
        if (fieldError.isBindingFailure()) {
            return ErrorCode.MALFORMED_REQUEST;
        }
        try {
            return ErrorCode.valueOf(fieldError.getDefaultMessage());
        } catch (IllegalArgumentException exception) {
            return ErrorCode.INVALID_KEY;
        }
    }

    private String mapAttributes(String message, Map<String, Object> attributes) {
        String mappedMessage = message;
        if (attributes.containsKey(MIN_ATTRIBUTE)) {
            mappedMessage =
                    mappedMessage.replace("{" + MIN_ATTRIBUTE + "}", String.valueOf(attributes.get(MIN_ATTRIBUTE)));
        }
        if (attributes.containsKey(MAX_ATTRIBUTE)) {
            mappedMessage =
                    mappedMessage.replace("{" + MAX_ATTRIBUTE + "}", String.valueOf(attributes.get(MAX_ATTRIBUTE)));
        }
        return mappedMessage;
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(ErrorCode errorCode) {
        return errorResponse(errorCode, messageResolver.resolve(errorCode));
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(ErrorCode errorCode, String message) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(Objects.requireNonNull(message))
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }
}
