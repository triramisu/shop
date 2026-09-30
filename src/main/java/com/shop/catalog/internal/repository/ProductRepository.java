package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.CategoryStatus;
import com.shop.catalog.internal.entity.Product;
import com.shop.catalog.internal.entity.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends Repository<Product, UUID> {

    <S extends Product> S save(S product);

    <S extends Product> S saveAndFlush(S product);

    Optional<Product> findById(UUID id);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :id and product.deletedAt is null")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"category", "variants"})
    @Query("select product from Product product where product.id = :id and product.deletedAt is null")
    Optional<Product> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"category", "variants"})
    @Query("""
            select product
              from Product product
              join product.category category
             where product.id = :id
               and product.status = :productStatus
               and product.deletedAt is null
               and category.status = :categoryStatus
               and category.deletedAt is null
            """)
    Optional<Product> findStorefrontById(
            @Param("id") UUID id,
            @Param("productStatus") ProductStatus productStatus,
            @Param("categoryStatus") CategoryStatus categoryStatus);

    @Query(value = """
                    select product
                      from Product product
                      join fetch product.category category
                     where product.deletedAt is null
                       and (:categoryId is null or category.id = :categoryId)
                       and (:status is null or product.status = :status)
                       and (:keyword is null
                            or lower(product.name) like :keyword escape '!'
                            or lower(product.slug) like :keyword escape '!'
                            or exists (
                                select variant.id
                                  from ProductVariant variant
                                 where variant.product = product
                                   and variant.deletedAt is null
                                   and lower(variant.sku) like :keyword escape '!'
                            ))
                    """, countQuery = """
                    select count(product)
                      from Product product
                     where product.deletedAt is null
                       and (:categoryId is null or product.category.id = :categoryId)
                       and (:status is null or product.status = :status)
                       and (:keyword is null
                            or lower(product.name) like :keyword escape '!'
                            or lower(product.slug) like :keyword escape '!'
                            or exists (
                                select variant.id
                                  from ProductVariant variant
                                 where variant.product = product
                                   and variant.deletedAt is null
                                   and lower(variant.sku) like :keyword escape '!'
                            ))
                    """)
    Page<Product> searchLike(
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("status") com.shop.catalog.internal.entity.ProductStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"category", "variants"})
    @Query("select distinct product from Product product where product.id in :ids and product.deletedAt is null")
    List<Product> findAllDetailedByIdIn(@Param("ids") Collection<UUID> ids);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);

    boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);
}
