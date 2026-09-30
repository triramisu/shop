package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.ProductVariant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends Repository<ProductVariant, UUID> {

    Optional<ProductVariant> findById(UUID id);

    Optional<ProductVariant> findBySkuIgnoreCase(String sku);

    @Query("""
            select variant
              from ProductVariant variant
              join variant.product product
             where lower(variant.sku) = lower(:sku)
               and variant.deletedAt is null
               and product.deletedAt is null
            """)
    Optional<ProductVariant> findInventoryReferenceBySkuIgnoreCase(@Param("sku") String sku);

    boolean existsBySkuIgnoreCase(String sku);
}
