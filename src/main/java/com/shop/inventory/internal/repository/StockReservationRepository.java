package com.shop.inventory.internal.repository;

import com.shop.inventory.internal.entity.StockReservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface StockReservationRepository extends Repository<StockReservation, UUID> {

    <S extends StockReservation> S saveAndFlush(S reservation);

    Optional<StockReservation> findById(UUID id);
}
