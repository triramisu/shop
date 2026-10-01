package com.shop.inventory.internal.repository;

import com.shop.inventory.internal.entity.StockItem;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends Repository<StockItem, UUID> {

    <S extends StockItem> S saveAndFlush(S stockItem);

    Optional<StockItem> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select stockItem from StockItem stockItem where stockItem.id = :stockItemId")
    Optional<StockItem> findByIdForUpdate(@Param("stockItemId") UUID stockItemId);

    boolean existsById(UUID id);

    boolean existsByProductVariantIdAndLocationCodeIgnoreCase(UUID productVariantId, String locationCode);

    Optional<StockItem> findByProductVariantIdAndLocationCodeIgnoreCase(UUID productVariantId, String locationCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StockItem stockItem
               set stockItem.reserved = stockItem.reserved + :quantity,
                   stockItem.version = stockItem.version + 1,
                   stockItem.updatedAt = :updatedAt
             where stockItem.id = :stockItemId
               and stockItem.onHand - stockItem.reserved >= :quantity
            """)
    int reserveIfAvailable(
            @Param("stockItemId") UUID stockItemId,
            @Param("quantity") long quantity,
            @Param("updatedAt") Instant updatedAt);

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
