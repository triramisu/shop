package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.CategoryStatus;
import com.shop.catalog.internal.entity.ProductStatus;
import com.shop.catalog.internal.entity.ProductVariant;
import com.shop.catalog.internal.entity.ProductVariantStatus;
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

    @Query("""
            select variant
              from ProductVariant variant
              join variant.product product
              join product.category category
             where lower(variant.sku) = lower(:sku)
               and variant.status = :variantStatus
               and variant.deletedAt is null
               and product.status = :productStatus
               and product.deletedAt is null
               and category.status = :categoryStatus
               and category.deletedAt is null
            """)
    Optional<ProductVariant> findSellableCartReferenceBySkuIgnoreCase(
            @Param("sku") String sku,
            @Param("variantStatus") ProductVariantStatus variantStatus,
            @Param("productStatus") ProductStatus productStatus,
            @Param("categoryStatus") CategoryStatus categoryStatus);

    boolean existsBySkuIgnoreCase(String sku);
}
