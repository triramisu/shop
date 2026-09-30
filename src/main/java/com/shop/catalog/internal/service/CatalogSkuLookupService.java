package com.shop.catalog.internal.service;

import com.shop.catalog.internal.repository.ProductVariantRepository;
import com.shop.catalog.inventory.CatalogSkuLookup;
import com.shop.catalog.inventory.CatalogSkuReference;
import java.util.Locale;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CatalogSkuLookupService implements CatalogSkuLookup {

    ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogSkuReference> findBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        String normalizedSku = sku.strip().toUpperCase(Locale.ROOT);
        return productVariantRepository
                .findInventoryReferenceBySkuIgnoreCase(normalizedSku)
                .map(variant -> new CatalogSkuReference(variant.getId(), variant.getSku()));
    }
}
