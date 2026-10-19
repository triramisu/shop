package com.shop.catalog.internal.service;

import com.shop.catalog.internal.entity.CategoryStatus;
import com.shop.catalog.internal.entity.ProductStatus;
import com.shop.catalog.internal.entity.ProductVariantStatus;
import com.shop.catalog.internal.repository.ProductVariantRepository;
import com.shop.catalog.order.CatalogCheckoutItemPrice;
import com.shop.catalog.order.CatalogCheckoutPricingLookup;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CatalogCheckoutPricingLookupService implements CatalogCheckoutPricingLookup {

    ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CatalogCheckoutItemPrice> findSellableByVariantIds(Set<UUID> productVariantIds) {
        if (productVariantIds == null || productVariantIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> requestedIds = Set.copyOf(productVariantIds);
        return productVariantRepository
                .findSellableCheckoutReferencesByIdIn(
                        requestedIds, ProductVariantStatus.ACTIVE, ProductStatus.PUBLISHED, CategoryStatus.ACTIVE)
                .stream()
                .map(variant -> new CatalogCheckoutItemPrice(
                        variant.getId(),
                        variant.getSku(),
                        variant.getName(),
                        variant.getPrice(),
                        variant.getCurrency(),
                        variant.getVersion()))
                .toList();
    }
}
