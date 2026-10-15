package com.shop.catalog.order;

import java.util.Optional;

public interface CatalogCartItemLookup {

    Optional<CatalogCartItemReference> findSellableBySku(String sku);
}
