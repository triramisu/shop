package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.entity.Category;
import com.shop.catalog.internal.entity.CategoryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CategoryRepository extends Repository<Category, UUID> {

    <S extends Category> S save(S category);

    <S extends Category> S saveAndFlush(S category);

    Optional<Category> findById(UUID id);

    Optional<Category> findByIdAndDeletedAtIsNull(UUID id);

    List<Category> findAllByDeletedAtIsNullOrderByNameAscIdAsc();

    List<Category> findAllByStatusAndDeletedAtIsNullOrderByNameAscIdAsc(CategoryStatus status);

    Optional<Category> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);
}
