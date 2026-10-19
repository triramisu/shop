package com.shop.catalog.internal.image.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

class ProductImageValidatorTests {

    private CatalogImageProperties properties;
    private ProductImageValidator validator;

    @BeforeEach
    void setUp() {
        properties = new CatalogImageProperties();
        validator = new ProductImageValidator(properties);
    }

    @Test
    void acceptsRealPngAndJpegAndSanitizesClientFilename() throws Exception {
        ValidatedProductImage png = validator.validate(
                new MockMultipartFile("files", "C:\\fakepath\\photo.png", "IMAGE/PNG", imageBytes("png", 2, 2)));
        ValidatedProductImage jpeg =
                validator.validate(new MockMultipartFile("files", null, "image/jpeg", imageBytes("jpg", 2, 2)));

        assertThat(png.originalFilename()).isEqualTo("photo.png");
        assertThat(png.contentType()).isEqualTo("image/png");
        assertThat(png.extension()).isEqualTo("png");
        assertThat(jpeg.originalFilename()).isEqualTo("image.jpg");
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpeg.sizeBytes()).isPositive();

        byte[] returnedContent = png.content();
        returnedContent[0] = 0;
        assertThat(png.content()[0]).isEqualTo((byte) 0x89);

        ValidatedProductImage equalPng = new ValidatedProductImage("photo.png", "image/png", "png", png.content());
        assertThat(png)
                .isEqualTo(equalPng)
                .hasSameHashCodeAs(equalPng)
                .isNotEqualTo(new ValidatedProductImage("other.png", "image/png", "png", png.content()))
                .isNotEqualTo(new ValidatedProductImage("photo.png", "image/jpeg", "png", png.content()))
                .isNotEqualTo(new ValidatedProductImage("photo.png", "image/png", "jpg", png.content()))
                .isNotEqualTo(new ValidatedProductImage("photo.png", "image/png", "png", jpeg.content()));
        assertThat(png.toString())
                .contains("photo.png", "image/png", "extension=png", "contentLength=", "contentHash=")
                .doesNotContain("[-119, 80, 78, 71");
    }

    @Test
    void rejectsMissingEmptyAndUnsupportedFiles() {
        assertError(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID, () -> validator.validate(null));
        assertError(
                ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID,
                () -> validator.validate(new MockMultipartFile("files", "empty.png", "image/png", new byte[0])));
        assertError(
                ErrorCode.PRODUCT_IMAGE_TYPE_INVALID,
                () -> validator.validate(new MockMultipartFile("files", "image.gif", "image/gif", new byte[] {1})));
    }

    @Test
    void rejectsOversizedSpoofedCorruptAndExcessivePixelImages() throws Exception {
        byte[] png = imageBytes("png", 2, 2);
        properties.setMaxFileSize(DataSize.ofBytes(png.length - 1L));
        assertError(
                ErrorCode.PRODUCT_IMAGE_TOO_LARGE,
                () -> validator.validate(new MockMultipartFile("files", "large.png", "image/png", png)));

        properties.setMaxFileSize(DataSize.ofMegabytes(5));
        assertError(
                ErrorCode.PRODUCT_IMAGE_TYPE_INVALID,
                () -> validator.validate(new MockMultipartFile("files", "spoofed.jpg", "image/jpeg", png)));
        assertError(
                ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID,
                () -> validator.validate(new MockMultipartFile("files", "corrupt.png", "image/png", new byte[] {
                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
                })));

        properties.setMaxPixels(3);
        assertError(
                ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID,
                () -> validator.validate(new MockMultipartFile("files", "pixels.png", "image/png", png)));
    }

    @Test
    void mapsUnreadableMultipartContentToAControlledError() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(10L);
        when(file.getInputStream()).thenThrow(new IOException("simulated read failure"));

        assertError(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID, () -> validator.validate(file));
    }

    private void assertError(ErrorCode expected, ThrowingOperation operation) {
        assertThatThrownBy(operation::execute)
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
    }

    private byte[] imageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, format, output)).isTrue();
            return output.toByteArray();
        }
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void execute() throws Exception;
    }
}
