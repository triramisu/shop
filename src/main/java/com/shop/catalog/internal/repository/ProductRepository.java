package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends Repository<Product, UUID> {

    <S extends Product> S save(S product);

    <S extends Product> S saveAndFlush(S product);

    Optional<Product> findById(UUID id);

    @EntityGraph(attributePaths = {"category", "variants"})
    @Query("select product from Product product where product.id = :id and product.deletedAt is null")
    Optional<Product> findDetailedById(@Param("id") UUID id);

    @Query(value = """
                    select product
                      from Product product
                      join fetch product.category category
                     where product.deletedAt is null
                       and (:categoryId is null or category.id = :categoryId)
                       and (:status is null or product.status = :status)
                       and (:keyword is null
                            or lower(product.name) like :keyword escape '!'
                            or lower(product.slug) like :keyword escape '!')
                    """, countQuery = """
                    select count(product)
                      from Product product
                     where product.deletedAt is null
                       and (:categoryId is null or product.category.id = :categoryId)
                       and (:status is null or product.status = :status)
                       and (:keyword is null
                            or lower(product.name) like :keyword escape '!'
                            or lower(product.slug) like :keyword escape '!')
                    """)
    Page<Product> search(
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("status") com.shop.catalog.internal.entity.ProductStatus status,
            Pageable pageable);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);

    boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);
}
