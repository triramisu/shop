package com.shop.catalog.internal.image.storage;

import java.util.Arrays;
import java.util.Objects;

public record ObjectStorageUpload(String objectKey, String contentType, byte[] content) {

    public ObjectStorageUpload {
        objectKey = requireText(objectKey, "object key");
        contentType = requireText(contentType, "content type");
        content = Objects.requireNonNull(content, "content is required").clone();
        if (content.length == 0) {
            throw new IllegalArgumentException("content cannot be empty");
        }
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object
                instanceof ObjectStorageUpload(String thatObjectKey, String thatContentType, byte[] thatContent))) {
            return false;
        }
        return objectKey.equals(thatObjectKey)
                && contentType.equals(thatContentType)
                && Arrays.equals(content, thatContent);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(objectKey, contentType);
        return 31 * result + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "ObjectStorageUpload[objectKey=" + objectKey + ", contentType=" + contentType + ", contentLength="
                + content.length + ", contentHash=" + Arrays.hashCode(content) + "]";
    }

    private static String requireText(String value, String fieldName) {
        String requiredValue =
                Objects.requireNonNull(value, fieldName + " is required").strip();
        if (requiredValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return requiredValue;
    }
}
