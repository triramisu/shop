package com.shop.catalog.inventory;

import java.util.Optional;

public interface CatalogSkuLookup {

    Optional<CatalogSkuReference> findBySku(String sku);
}
