package com.shop.inventory.internal.service;

import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.internal.entity.StockReservationOperation;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockReservationTransactionService {

    static final String RESERVATION_REASON = "Giữ tồn kho";

    StockItemRepository stockItemRepository;
    StockReservationRepository stockReservationRepository;
    StockMovementRecorder movementRecorder;
    StockReservationIdempotencyStore idempotencyStore;
    StockReservationCommandFingerprint commandFingerprint;
    PlatformTransactionManager transactionManager;
    InventoryReservationProperties properties;

    public StockReservationResult reserve(ReserveStockCommand command, Instant issuedAt) {
        TransactionTemplate transaction = transactionTemplate();
        return Objects.requireNonNull(
                transaction.execute(status -> reserveInTransaction(command, issuedAt)),
                "reservation transaction returned no result");
    }

    public StockReservationResult replayExisting(ReserveStockCommand command) {
        TransactionTemplate transaction = transactionTemplate();
        return Objects.requireNonNull(
                transaction.execute(status -> replayExistingInTransaction(command)),
                "reservation replay transaction returned no result");
    }

    private StockReservationResult reserveInTransaction(ReserveStockCommand command, Instant issuedAt) {
        String fingerprint = commandFingerprint.reserve(command);
        if (stockReservationRepository.existsById(command.reservationId())) {
            return replayExistingLocked(command, fingerprint);
        }
        validateExpiration(command.expiresAt(), issuedAt);

        int updatedRows = stockItemRepository.reserveIfAvailable(command.stockItemId(), command.quantity(), issuedAt);
        if (updatedRows == 0) {
            if (!stockItemRepository.existsById(command.stockItemId())) {
                throw new AppException(ErrorCode.STOCK_ITEM_NOT_FOUND);
            }
            throw new AppException(ErrorCode.INVENTORY_INSUFFICIENT_STOCK);
        }

        StockItem stockItem = stockItemRepository
                .findById(command.stockItemId())
                .orElseThrow(() -> new AppException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        StockReservation reservation;
        try {
            reservation = stockReservationRepository.saveAndFlush(StockReservation.issue(
                    command.reservationId(), stockItem, command.quantity(), command.expiresAt(), issuedAt));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.STOCK_RESERVATION_ALREADY_EXISTS);
        }
        movementRecorder.record(
                stockItem,
                StockMovementType.RESERVATION,
                0,
                command.quantity(),
                RESERVATION_REASON,
                command.reservationId().toString(),
                issuedAt);
        StockReservationResult result = new StockReservationResult(
                reservation.getId(),
                stockItem.getId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                stockItem.getOnHand(),
                stockItem.getReserved(),
                stockItem.getAvailable());
        idempotencyStore.remember(StockReservationOperation.RESERVE, fingerprint, result, issuedAt);
        return result;
    }

    private StockReservationResult replayExistingInTransaction(ReserveStockCommand command) {
        String fingerprint = commandFingerprint.reserve(command);
        return replayExistingLocked(command, fingerprint);
    }

    private StockReservationResult replayExistingLocked(ReserveStockCommand command, String fingerprint) {
        StockReservation reservation = stockReservationRepository
                .findByIdForUpdate(command.reservationId())
                .orElseThrow(() -> new AppException(ErrorCode.STOCK_RESERVATION_REPLAY_UNAVAILABLE));
        return idempotencyStore
                .findReplay(command.reservationId(), StockReservationOperation.RESERVE, fingerprint)
                .orElseGet(() -> recoverExisting(command, reservation, fingerprint));
    }

    private StockReservationResult recoverExisting(
            ReserveStockCommand command, StockReservation reservation, String fingerprint) {
        if (!reservation.getStockItem().getId().equals(command.stockItemId())
                || reservation.getQuantity() != command.quantity()
                || !reservation.getExpiresAt().equals(command.expiresAt())) {
            throw new AppException(ErrorCode.STOCK_RESERVATION_IDEMPOTENCY_CONFLICT);
        }
        return idempotencyStore.recoverLegacy(
                reservation,
                StockReservationOperation.RESERVE,
                fingerprint,
                StockMovementType.RESERVATION,
                StockReservationStatus.RESERVED);
    }

    private void validateExpiration(Instant expiresAt, Instant issuedAt) {
        Duration duration = Duration.between(issuedAt, expiresAt);
        if (duration.isZero() || duration.isNegative() || duration.compareTo(properties.getMaxDuration()) > 0) {
            throw new AppException(ErrorCode.INVENTORY_RESERVATION_EXPIRATION_INVALID);
        }
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(
                Math.toIntExact(properties.getTransactionTimeout().toSeconds()));
        return transaction;
    }
}
