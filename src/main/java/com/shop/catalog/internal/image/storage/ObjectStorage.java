package com.shop.catalog.internal.image.storage;

public interface ObjectStorage {

    void store(ObjectStorageUpload upload);

    void delete(String objectKey);
}
