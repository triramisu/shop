package com.shop.catalog.internal.service;

import com.shop.catalog.internal.entity.CategoryStatus;
import com.shop.catalog.internal.entity.ProductStatus;
import com.shop.catalog.internal.entity.ProductVariantStatus;
import com.shop.catalog.internal.repository.ProductVariantRepository;
import com.shop.catalog.order.CatalogCartItemLookup;
import com.shop.catalog.order.CatalogCartItemReference;
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
public class CatalogCartItemLookupService implements CatalogCartItemLookup {

    ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogCartItemReference> findSellableBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        String normalizedSku = sku.strip().toUpperCase(Locale.ROOT);
        return productVariantRepository
                .findSellableCartReferenceBySkuIgnoreCase(
                        normalizedSku, ProductVariantStatus.ACTIVE, ProductStatus.PUBLISHED, CategoryStatus.ACTIVE)
                .map(variant -> new CatalogCartItemReference(variant.getId(), variant.getSku()));
    }
}
