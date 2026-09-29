package com.shop.catalog.internal.image.configuration;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class CatalogImagePropertiesTests {

    @Test
    void acceptsValidFilesystemAndMemoryConfigurations() {
        CatalogImageProperties filesystem = validProperties();
        CatalogImageProperties memory = validProperties();
        memory.getStorage().setType(CatalogImageProperties.StorageType.MEMORY);
        memory.getStorage().setFilesystemRoot(null);

        assertThatNoException().isThrownBy(filesystem::validate);
        assertThatNoException().isThrownBy(memory::validate);
    }

    @Test
    void rejectsInvalidCountSizePixelAndStorageSettings() {
        CatalogImageProperties invalidCount = validProperties();
        invalidCount.setMaxFilesPerUpload(0);
        assertInvalid(invalidCount);

        CatalogImageProperties inconsistentCount = validProperties();
        inconsistentCount.setMaxFilesPerProduct(4);
        assertInvalid(inconsistentCount);

        CatalogImageProperties invalidSize = validProperties();
        invalidSize.setMaxFileSize(DataSize.ofBytes(0));
        assertInvalid(invalidSize);

        CatalogImageProperties excessiveSize = validProperties();
        excessiveSize.setMaxFileSize(DataSize.ofBytes(Integer.MAX_VALUE));
        assertInvalid(excessiveSize);

        CatalogImageProperties invalidPixels = validProperties();
        invalidPixels.setMaxPixels(0);
        assertInvalid(invalidPixels);

        CatalogImageProperties missingStorage = validProperties();
        missingStorage.setStorage(null);
        assertInvalid(missingStorage);

        CatalogImageProperties missingType = validProperties();
        missingType.getStorage().setType(null);
        assertInvalid(missingType);

        CatalogImageProperties missingRoot = validProperties();
        missingRoot.getStorage().setFilesystemRoot(null);
        assertInvalid(missingRoot);
    }

    private CatalogImageProperties validProperties() {
        CatalogImageProperties properties = new CatalogImageProperties();
        properties.getStorage().setFilesystemRoot(Path.of("objects"));
        return properties;
    }

    private void assertInvalid(CatalogImageProperties properties) {
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
