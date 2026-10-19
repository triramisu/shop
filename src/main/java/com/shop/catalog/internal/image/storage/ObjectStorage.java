package com.shop.catalog.internal.image.storage;

import java.net.URI;
import java.util.Optional;

public interface ObjectStorage {

    void store(ObjectStorageUpload upload);

    void delete(String objectKey);

    Optional<URI> createReadUrl(String objectKey);
}
