package com.shop.inventory.internal.repository;

import com.shop.inventory.internal.entity.StockReservationIdempotencyRecord;
import com.shop.inventory.internal.entity.StockReservationOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface StockReservationIdempotencyRepository extends Repository<StockReservationIdempotencyRecord, UUID> {

    <S extends StockReservationIdempotencyRecord> S saveAndFlush(S record);

    Optional<StockReservationIdempotencyRecord> findByReservationIdAndOperation(
            UUID reservationId, StockReservationOperation operation);

    long countByReservationId(UUID reservationId);
}
