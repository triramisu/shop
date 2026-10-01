package com.shop.catalog.order;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CatalogCheckoutPricingLookup {

    List<CatalogCheckoutItemPrice> findSellableByVariantIds(Set<UUID> productVariantIds);
}
