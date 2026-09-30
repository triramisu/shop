package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductSearchQuery {

    Page<Product> search(ProductSearchCriteria criteria, Pageable pageable);
}
