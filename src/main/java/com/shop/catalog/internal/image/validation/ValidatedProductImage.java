package com.shop.catalog.internal.image.validation;

import java.util.Arrays;
import java.util.Objects;

public record ValidatedProductImage(String originalFilename, String contentType, String extension, byte[] content) {

    public ValidatedProductImage {
        Objects.requireNonNull(originalFilename, "original filename is required");
        Objects.requireNonNull(contentType, "content type is required");
        Objects.requireNonNull(extension, "extension is required");
        content = Objects.requireNonNull(content, "content is required").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long sizeBytes() {
        return content.length;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object
                instanceof
                ValidatedProductImage(
                        String thatFilename,
                        String thatContentType,
                        String thatExtension,
                        byte[] thatContent))) {
            return false;
        }
        return originalFilename.equals(thatFilename)
                && contentType.equals(thatContentType)
                && extension.equals(thatExtension)
                && Arrays.equals(content, thatContent);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(originalFilename, contentType, extension);
        return 31 * result + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "ValidatedProductImage[originalFilename=" + originalFilename + ", contentType=" + contentType
                + ", extension=" + extension + ", contentLength=" + content.length + ", contentHash="
                + Arrays.hashCode(content) + "]";
    }
}
