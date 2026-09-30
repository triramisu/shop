package com.shop.inventory.internal.repository;

import com.shop.inventory.internal.entity.StockItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends Repository<StockItem, UUID> {

    <S extends StockItem> S saveAndFlush(S stockItem);

    Optional<StockItem> findById(UUID id);

    boolean existsByProductVariantIdAndLocationCodeIgnoreCase(UUID productVariantId, String locationCode);

    @Query("""
            select stockItem
              from StockItem stockItem
             where (:keyword is null
                    or lower(stockItem.sku) like :keyword escape '!'
                    or lower(stockItem.locationCode) like :keyword escape '!')
               and (:locationCode is null or stockItem.locationCode = :locationCode)
            """)
    Page<StockItem> search(
            @Param("keyword") String keyword, @Param("locationCode") String locationCode, Pageable pageable);
}
