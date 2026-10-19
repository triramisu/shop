package com.shop.inventory.internal.repository;

import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.reservation.StockReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StockReservationRepository extends Repository<StockReservation, UUID> {

    <S extends StockReservation> S saveAndFlush(S reservation);

    Optional<StockReservation> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from StockReservation reservation where reservation.id = :reservationId")
    Optional<StockReservation> findByIdForUpdate(@Param("reservationId") UUID reservationId);

    boolean existsById(UUID id);

    long countByStockItemId(UUID stockItemId);

    @Transactional(readOnly = true)
    @Query("""
            select reservation.id
              from StockReservation reservation
             where reservation.status = :status
               and reservation.expiresAt <= :cutoff
             order by reservation.expiresAt asc, reservation.id asc
            """)
    List<UUID> findIdsByStatusAndExpiration(
            @Param("status") StockReservationStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);
}
