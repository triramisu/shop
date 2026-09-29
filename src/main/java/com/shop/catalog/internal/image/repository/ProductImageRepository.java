package com.shop.catalog.internal.image.repository;

import com.shop.catalog.internal.image.entity.ProductImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ProductImageRepository extends Repository<ProductImage, UUID> {

    <S extends ProductImage> List<S> saveAllAndFlush(Iterable<S> images);

    <S extends ProductImage> S saveAndFlush(S image);

    List<ProductImage> findAllByProductIdOrderByDisplayOrderAsc(UUID productId);

    Optional<ProductImage> findByIdAndProductId(UUID id, UUID productId);

    long countByProductId(UUID productId);

    void delete(ProductImage image);

    void flush();
}
