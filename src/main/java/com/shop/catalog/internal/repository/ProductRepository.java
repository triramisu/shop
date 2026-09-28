package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface ProductRepository extends Repository<Product, UUID> {

    <S extends Product> S save(S product);

    Optional<Product> findById(UUID id);

    @EntityGraph(attributePaths = {"category", "variants"})
    Optional<Product> findDetailedById(UUID id);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);
}
