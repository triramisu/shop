package com.shop.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAppExceptionToApiResponse() {
        AppException exception = new AppException(ErrorCode.RESOURCE_NOT_FOUND);

        var response = handler.handleAppException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(1011);
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        var response = handler.handleUnexpectedException(new IllegalStateException("sensitive internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(9999);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());
        assertThat(response.getBody().getMessage()).doesNotContain("sensitive internal detail");
    }
}
