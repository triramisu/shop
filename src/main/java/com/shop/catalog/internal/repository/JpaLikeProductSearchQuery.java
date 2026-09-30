package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.Product;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.catalog.search", name = "strategy", havingValue = "like")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JpaLikeProductSearchQuery implements ProductSearchQuery {

    ProductRepository productRepository;

    @Override
    public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
        return productRepository.searchLike(
                criteria.containsLikePattern(), criteria.categoryId(), criteria.status(), pageable);
    }
}
