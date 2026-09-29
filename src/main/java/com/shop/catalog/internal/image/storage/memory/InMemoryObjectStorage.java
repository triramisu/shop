package com.shop.catalog.internal.image.storage.memory;

import com.shop.catalog.internal.image.storage.ObjectStorage;
import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@ConditionalOnProperty(prefix = "app.catalog.images.storage", name = "type", havingValue = "memory")
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, ObjectStorageUpload> objects = new ConcurrentHashMap<>();
    private final AtomicInteger successfulStoresBeforeFailure = new AtomicInteger(-1);

    @Override
    public void store(ObjectStorageUpload upload) {
        if (successfulStoresBeforeFailure.get() == 0) {
            throw new ObjectStorageException("Simulated object storage failure");
        }
        if (successfulStoresBeforeFailure.get() > 0) {
            successfulStoresBeforeFailure.decrementAndGet();
        }
        ObjectStorageUpload previous = objects.putIfAbsent(upload.objectKey(), upload);
        if (previous != null) {
            throw new ObjectStorageException("Object key already exists");
        }
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
    }

    public boolean contains(String objectKey) {
        return objects.containsKey(objectKey);
    }

    public int size() {
        return objects.size();
    }

    public void failAfterSuccessfulStores(int successfulStores) {
        if (successfulStores < 0) {
            throw new IllegalArgumentException("successfulStores cannot be negative");
        }
        successfulStoresBeforeFailure.set(successfulStores);
    }

    public void clear() {
        objects.clear();
        successfulStoresBeforeFailure.set(-1);
    }
}
