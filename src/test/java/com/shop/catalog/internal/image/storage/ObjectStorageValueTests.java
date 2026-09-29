package com.shop.catalog.internal.image.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.catalog.internal.image.storage.memory.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;

class ObjectStorageValueTests {

    @Test
    void uploadValueDefensivelyCopiesItsContent() {
        byte[] content = {1, 2, 3};
        ObjectStorageUpload upload = new ObjectStorageUpload("object-key", "image/png", content);
        ObjectStorageUpload equalUpload = new ObjectStorageUpload("object-key", "image/png", new byte[] {1, 2, 3});
        content[0] = 9;
        byte[] returned = upload.content();
        returned[1] = 9;

        assertThat(upload.content()).containsExactly(1, 2, 3);
        assertThat(upload)
                .isEqualTo(equalUpload)
                .hasSameHashCodeAs(equalUpload)
                .isNotEqualTo(new ObjectStorageUpload("another-key", "image/png", new byte[] {1, 2, 3}))
                .isNotEqualTo(new ObjectStorageUpload("object-key", "image/jpeg", new byte[] {1, 2, 3}))
                .isNotEqualTo(new ObjectStorageUpload("object-key", "image/png", new byte[] {3, 2, 1}));
        assertThat(upload.toString())
                .contains("object-key", "image/png", "contentLength=3", "contentHash=")
                .doesNotContain("[1, 2, 3]");
    }

    @Test
    void uploadValueRejectsMissingFieldsAndEmptyContent() {
        assertThatThrownBy(() -> new ObjectStorageUpload(" ", "image/png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectStorageUpload("key", " ", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjectStorageUpload("key", "image/png", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inMemoryAdapterSupportsInspectionCollisionAndFailureSimulation() {
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        ObjectStorageUpload first = new ObjectStorageUpload("first", "image/png", new byte[] {1});
        storage.store(first);

        assertThat(storage.contains("first")).isTrue();
        assertThat(storage.size()).isEqualTo(1);
        assertThatThrownBy(() -> storage.store(first)).isInstanceOf(ObjectStorageException.class);
        assertThatThrownBy(() -> storage.failAfterSuccessfulStores(-1)).isInstanceOf(IllegalArgumentException.class);

        storage.failAfterSuccessfulStores(0);
        ObjectStorageUpload second = new ObjectStorageUpload("second", "image/png", new byte[] {2});
        assertThatThrownBy(() -> storage.store(second)).isInstanceOf(ObjectStorageException.class);
        storage.delete("first");
        storage.clear();
        assertThat(storage.size()).isZero();
    }
}
