package com.shop.catalog.internal.image.validation;

import com.shop.catalog.internal.image.configuration.CatalogImageProperties;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductImageValidator {

    static final String JPEG_CONTENT_TYPE = "image/jpeg";
    static final String PNG_CONTENT_TYPE = "image/png";

    CatalogImageProperties properties;

    public ValidatedProductImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
        }
        String declaredContentType = normalizeContentType(file.getContentType());
        requireSupportedContentType(declaredContentType);

        long maximumBytes = properties.getMaxFileSize().toBytes();
        if (file.getSize() > maximumBytes) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_TOO_LARGE);
        }

        byte[] content = readBounded(file, (int) maximumBytes);
        String detectedContentType = detectContentType(content);
        if (!declaredContentType.equals(detectedContentType)) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_TYPE_INVALID);
        }
        validateImageStructure(content, detectedContentType);

        String extension = detectedContentType.equals(JPEG_CONTENT_TYPE) ? "jpg" : "png";
        return new ValidatedProductImage(
                sanitizeFilename(file.getOriginalFilename(), extension), detectedContentType, extension, content);
    }

    private byte[] readBounded(MultipartFile file, int maximumBytes) {
        try (var inputStream = file.getInputStream()) {
            byte[] content = inputStream.readNBytes(maximumBytes + 1);
            if (content.length == 0) {
                throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
            }
            if (content.length > maximumBytes) {
                throw new AppException(ErrorCode.PRODUCT_IMAGE_TOO_LARGE);
            }
            return content;
        } catch (IOException exception) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
        }
    }

    private String detectContentType(byte[] content) {
        if (hasPngSignature(content)) {
            return PNG_CONTENT_TYPE;
        }
        if (hasJpegSignature(content)) {
            return JPEG_CONTENT_TYPE;
        }
        throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
    }

    private void validateImageStructure(byte[] content, String detectedContentType) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (imageInput == null) {
                throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                requireMatchingFormat(reader.getFormatName(), detectedContentType);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > properties.getMaxPixels()) {
                    throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
                }
                BufferedImage decodedImage = reader.read(0);
                if (decodedImage == null || decodedImage.getWidth() != width || decodedImage.getHeight() != height) {
                    throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AppException appException) {
                throw appException;
            }
            throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
        }
    }

    private void requireMatchingFormat(String formatName, String detectedContentType) {
        String normalizedFormat = formatName.toLowerCase(Locale.ROOT);
        boolean matches = detectedContentType.equals(PNG_CONTENT_TYPE)
                ? normalizedFormat.equals("png")
                : normalizedFormat.equals("jpeg") || normalizedFormat.equals("jpg");
        if (!matches) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_CONTENT_INVALID);
        }
    }

    private boolean hasPngSignature(byte[] content) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasJpegSignature(byte[] content) {
        return content.length >= 3
                && content[0] == (byte) 0xff
                && content[1] == (byte) 0xd8
                && content[2] == (byte) 0xff;
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
    }

    private void requireSupportedContentType(String contentType) {
        if (!contentType.equals(JPEG_CONTENT_TYPE) && !contentType.equals(PNG_CONTENT_TYPE)) {
            throw new AppException(ErrorCode.PRODUCT_IMAGE_TYPE_INVALID);
        }
    }

    private String sanitizeFilename(String originalFilename, String extension) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "image." + extension;
        }
        String normalized = originalFilename.replace('\\', '/');
        normalized = normalized
                .substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .strip();
        if (normalized.isEmpty()) {
            return "image." + extension;
        }
        return normalized.length() <= 255 ? normalized : normalized.substring(normalized.length() - 255);
    }
}
