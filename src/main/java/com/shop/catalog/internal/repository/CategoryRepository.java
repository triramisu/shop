package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.Category;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CategoryRepository extends Repository<Category, UUID> {

    <S extends Category> S save(S category);

    Optional<Category> findById(UUID id);

    Optional<Category> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsBySlugIgnoreCase(String slug);
}
