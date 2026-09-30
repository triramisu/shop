package com.shop.inventory.internal.repository;

import com.shop.inventory.internal.entity.StockMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface StockMovementRepository extends Repository<StockMovement, UUID> {

    <S extends StockMovement> S saveAndFlush(S movement);

    Page<StockMovement> findByStockItemId(UUID stockItemId, Pageable pageable);
}
