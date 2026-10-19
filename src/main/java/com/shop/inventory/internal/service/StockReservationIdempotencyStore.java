package com.shop.inventory.internal.service;

import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.entity.StockMovement;
import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.internal.entity.StockReservationIdempotencyRecord;
import com.shop.inventory.internal.entity.StockReservationOperation;
import com.shop.inventory.internal.repository.StockMovementRepository;
import com.shop.inventory.internal.repository.StockReservationIdempotencyRepository;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class StockReservationIdempotencyStore {

    StockReservationIdempotencyRepository idempotencyRepository;
    StockMovementRepository stockMovementRepository;

    Optional<StockReservationResult> findReplay(
            UUID reservationId, StockReservationOperation operation, String fingerprint) {
        return idempotencyRepository
                .findByReservationIdAndOperation(reservationId, operation)
                .map(storedRecord -> validateAndMap(storedRecord, fingerprint));
    }

    StockReservationResult recoverLegacy(
            StockReservation reservation,
            StockReservationOperation operation,
            String fingerprint,
            StockMovementType movementType,
            StockReservationStatus resultStatus) {
        StockMovement movement = stockMovementRepository
                .findFirstByStockItemIdAndMovementTypeAndReferenceIdOrderByOccurredAtAsc(
                        reservation.getStockItem().getId(),
                        movementType,
                        reservation.getId().toString())
                .orElseThrow(() -> new AppException(ErrorCode.STOCK_RESERVATION_REPLAY_UNAVAILABLE));
        StockReservationResult result = new StockReservationResult(
                reservation.getId(),
                reservation.getStockItem().getId(),
                reservation.getQuantity(),
                resultStatus,
                reservation.getExpiresAt(),
                movement.getOnHandAfter(),
                movement.getReservedAfter(),
                movement.getOnHandAfter() - movement.getReservedAfter());
        remember(operation, fingerprint, result, movement.getOccurredAt());
        return result;
    }

    void remember(
            StockReservationOperation operation,
            String fingerprint,
            StockReservationResult result,
            Instant completedAt) {
        idempotencyRepository.saveAndFlush(StockReservationIdempotencyRecord.complete(
                result.reservationId(), operation, fingerprint, result, completedAt));
    }

    private StockReservationResult validateAndMap(StockReservationIdempotencyRecord storedRecord, String fingerprint) {
        if (!storedRecord.hasFingerprint(fingerprint)) {
            throw new AppException(ErrorCode.STOCK_RESERVATION_IDEMPOTENCY_CONFLICT);
        }
        return storedRecord.toResult();
    }
}
