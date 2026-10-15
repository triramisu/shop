package com.shop.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import com.shop.shared.error.ErrorMessageResolver;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTests {

    private final ErrorMessageResolver messageResolver = createMessageResolver();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messageResolver);

    @Test
    void mapsAppExceptionToApiResponse() {
        AppException exception = new AppException(ErrorCode.RESOURCE_NOT_FOUND);

        var response = handler.handleAppException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(1011);
        assertThat(response.getBody().getMessage()).isEqualTo("Không tìm thấy tài nguyên");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        var response = handler.handleUnexpectedException(new IllegalStateException("sensitive internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(9999);
        assertThat(response.getBody().getMessage())
                .isEqualTo(messageResolver.resolve(ErrorCode.UNCATEGORIZED_EXCEPTION));
        assertThat(response.getBody().getMessage()).doesNotContain("sensitive internal detail");
    }

    @Test
    void mapsMultipartLimitToTheCatalogImageError() {
        var response = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(1130);
        assertThat(response.getBody().getMessage()).isEqualTo("Kích thước ảnh vượt quá giới hạn cho phép");
    }

    private static ErrorMessageResolver createMessageResolver() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("message");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return new ErrorMessageResolver(messageSource);
    }
}
