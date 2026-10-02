package com.shop.inventory.internal.service;

import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.internal.entity.StockReservationOperation;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.ConfirmStockReservationCommand;
import com.shop.inventory.reservation.ReleaseStockReservationCommand;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockReservationLifecycleTransactionService {

    static final String CONFIRMATION_REASON = "Xác nhận xuất kho";
    static final String RELEASE_REASON = "Hủy giữ tồn kho";
    static final String EXPIRATION_REASON = "Hết hạn giữ tồn kho";

    StockItemRepository stockItemRepository;
    StockReservationRepository stockReservationRepository;
    StockMovementRecorder movementRecorder;
    StockReservationLifecycleEventPublisher lifecycleEventPublisher;
    StockReservationIdempotencyStore idempotencyStore;
    StockReservationCommandFingerprint commandFingerprint;
    PlatformTransactionManager transactionManager;
    InventoryReservationProperties properties;

    public StockReservationLifecycleExecution confirm(ConfirmStockReservationCommand command, Instant confirmedAt) {
        return inNewTransaction(() -> confirmInTransaction(command, confirmedAt));
    }

    public StockReservationResult release(ReleaseStockReservationCommand command, Instant releasedAt) {
        return inNewTransaction(() -> releaseInTransaction(command, releasedAt));
    }

    public boolean expireIfDue(UUID reservationId, Instant expiredAt) {
        return inNewTransaction(() -> expireIfDueInTransaction(reservationId, expiredAt));
    }

    private StockReservationLifecycleExecution confirmInTransaction(
            ConfirmStockReservationCommand command, Instant confirmedAt) {
        StockReservation reservation = getReservationForUpdate(command.reservationId());
        String fingerprint = commandFingerprint.confirm(command);
        var replay =
                idempotencyStore.findReplay(command.reservationId(), StockReservationOperation.CONFIRM, fingerprint);
        if (replay.isPresent()) {
            return confirmationExecution(replay.get());
        }
        if (reservation.getStatus() == StockReservationStatus.CONFIRMED) {
            return confirmationExecution(idempotencyStore.recoverLegacy(
                    reservation,
                    StockReservationOperation.CONFIRM,
                    fingerprint,
                    StockMovementType.CONFIRMATION,
                    StockReservationStatus.CONFIRMED));
        }
        if (reservation.getStatus() == StockReservationStatus.EXPIRED) {
            return confirmationExecution(idempotencyStore.recoverLegacy(
                    reservation,
                    StockReservationOperation.CONFIRM,
                    fingerprint,
                    StockMovementType.EXPIRATION,
                    StockReservationStatus.EXPIRED));
        }
        requireReserved(reservation);
        StockItem stockItem = getStockItemForUpdate(reservation);
        if (reservation.isExpiredAt(confirmedAt)) {
            applyExpiration(reservation, stockItem, confirmedAt);
            StockReservationResult result = toResult(reservation, stockItem);
            idempotencyStore.remember(StockReservationOperation.CONFIRM, fingerprint, result, confirmedAt);
            return confirmationExecution(result);
        }

        stockItem.confirm(reservation.getQuantity());
        reservation.confirm(confirmedAt);
        persistTransition(
                reservation,
                stockItem,
                StockMovementType.CONFIRMATION,
                -reservation.getQuantity(),
                -reservation.getQuantity(),
                CONFIRMATION_REASON,
                confirmedAt);
        StockReservationResult result = toResult(reservation, stockItem);
        idempotencyStore.remember(StockReservationOperation.CONFIRM, fingerprint, result, confirmedAt);
        return confirmationExecution(result);
    }

    private StockReservationResult releaseInTransaction(ReleaseStockReservationCommand command, Instant releasedAt) {
        StockReservation reservation = getReservationForUpdate(command.reservationId());
        String fingerprint = commandFingerprint.release(command);
        var replay =
                idempotencyStore.findReplay(command.reservationId(), StockReservationOperation.RELEASE, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (reservation.getStatus() == StockReservationStatus.RELEASED) {
            return idempotencyStore.recoverLegacy(
                    reservation,
                    StockReservationOperation.RELEASE,
                    fingerprint,
                    StockMovementType.RELEASE,
                    StockReservationStatus.RELEASED);
        }
        if (reservation.getStatus() == StockReservationStatus.EXPIRED) {
            return idempotencyStore.recoverLegacy(
                    reservation,
                    StockReservationOperation.RELEASE,
                    fingerprint,
                    StockMovementType.EXPIRATION,
                    StockReservationStatus.EXPIRED);
        }
        requireReserved(reservation);
        StockItem stockItem = getStockItemForUpdate(reservation);

        stockItem.release(reservation.getQuantity());
        reservation.release();
        persistTransition(
                reservation,
                stockItem,
                StockMovementType.RELEASE,
                0,
                -reservation.getQuantity(),
                RELEASE_REASON,
                releasedAt);
        StockReservationResult result = toResult(reservation, stockItem);
        idempotencyStore.remember(StockReservationOperation.RELEASE, fingerprint, result, releasedAt);
        return result;
    }

    private boolean expireIfDueInTransaction(UUID reservationId, Instant expiredAt) {
        StockReservation reservation =
                stockReservationRepository.findByIdForUpdate(reservationId).orElse(null);
        if (reservation == null
                || reservation.getStatus() != StockReservationStatus.RESERVED
                || !reservation.isExpiredAt(expiredAt)) {
            return false;
        }

        StockItem stockItem = getStockItemForUpdate(reservation);
        applyExpiration(reservation, stockItem, expiredAt);
        return true;
    }

    private void applyExpiration(StockReservation reservation, StockItem stockItem, Instant expiredAt) {
        stockItem.release(reservation.getQuantity());
        reservation.expire(expiredAt);
        persistTransition(
                reservation,
                stockItem,
                StockMovementType.EXPIRATION,
                0,
                -reservation.getQuantity(),
                EXPIRATION_REASON,
                expiredAt);
    }

    private void persistTransition(
            StockReservation reservation,
            StockItem stockItem,
            StockMovementType movementType,
            long onHandDelta,
            long reservedDelta,
            String reason,
            Instant occurredAt) {
        StockReservationStatus previousStatus = StockReservationStatus.RESERVED;
        stockItemRepository.saveAndFlush(stockItem);
        stockReservationRepository.saveAndFlush(reservation);
        movementRecorder.record(
                stockItem,
                movementType,
                onHandDelta,
                reservedDelta,
                reason,
                reservation.getId().toString(),
                occurredAt);
        lifecycleEventPublisher.publish(reservation, previousStatus, occurredAt);
    }

    private StockReservation getReservationForUpdate(UUID reservationId) {
        return stockReservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() -> new AppException(ErrorCode.STOCK_RESERVATION_NOT_FOUND));
    }

    private StockItem getStockItemForUpdate(StockReservation reservation) {
        return stockItemRepository
                .findByIdForUpdate(reservation.getStockItem().getId())
                .orElseThrow(() -> new AppException(ErrorCode.STOCK_ITEM_NOT_FOUND));
    }

    private void requireReserved(StockReservation reservation) {
        if (reservation.getStatus() != StockReservationStatus.RESERVED) {
            throw new AppException(ErrorCode.STOCK_RESERVATION_STATE_INVALID);
        }
    }

    private StockReservationResult toResult(StockReservation reservation, StockItem stockItem) {
        return new StockReservationResult(
                reservation.getId(),
                stockItem.getId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                stockItem.getOnHand(),
                stockItem.getReserved(),
                stockItem.getAvailable());
    }

    private StockReservationLifecycleExecution confirmationExecution(StockReservationResult result) {
        StockReservationLifecycleExecution.Outcome outcome = result.status() == StockReservationStatus.EXPIRED
                ? StockReservationLifecycleExecution.Outcome.EXPIRED_DURING_CONFIRM
                : StockReservationLifecycleExecution.Outcome.CONFIRMED;
        return new StockReservationLifecycleExecution(result, outcome);
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(
                Math.toIntExact(properties.getTransactionTimeout().toSeconds()));
        return Objects.requireNonNull(
                transaction.execute(status -> action.get()), "reservation lifecycle transaction returned no result");
    }
}
