package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.ProductVariant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ProductVariantRepository extends Repository<ProductVariant, UUID> {

    Optional<ProductVariant> findById(UUID id);

    Optional<ProductVariant> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);
}
