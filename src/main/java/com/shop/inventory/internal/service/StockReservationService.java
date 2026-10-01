package com.shop.inventory.internal.service;

import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.reservation.ConfirmStockReservationCommand;
import com.shop.inventory.reservation.ReleaseStockReservationCommand;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationOperations;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockReservationService implements StockReservationOperations {

    static final String OUTCOME_METRIC = "shop.inventory.reservation.operations";
    static final String RETRY_METRIC = "shop.inventory.reservation.lock.retries";

    StockReservationTransactionService transactionService;
    StockReservationLifecycleTransactionService lifecycleTransactionService;
    InventoryReservationProperties properties;
    MeterRegistry meterRegistry;

    @Override
    public StockReservationResult reserve(ReserveStockCommand command) {
        Objects.requireNonNull(command, "reserve stock command is required");
        Instant issuedAt = Instant.now();
        validateExpiration(command.expiresAt(), issuedAt);
        StockReservationResult result = executeWithLockRetry(
                "reserve",
                command.reservationId(),
                command.stockItemId(),
                () -> transactionService.reserve(command, issuedAt));
        recordOutcome("reserve", "success");
        return result;
    }

    @Override
    public StockReservationResult confirm(ConfirmStockReservationCommand command) {
        Objects.requireNonNull(command, "confirm stock reservation command is required");
        StockReservationLifecycleExecution execution = executeWithLockRetry(
                "confirm",
                command.reservationId(),
                null,
                () -> lifecycleTransactionService.confirm(command, Instant.now()));
        if (execution.outcome() == StockReservationLifecycleExecution.Outcome.EXPIRED_DURING_CONFIRM) {
            recordOutcome("confirm", "expired");
            throw new AppException(ErrorCode.STOCK_RESERVATION_EXPIRED);
        }
        recordOutcome("confirm", "success");
        return execution.result();
    }

    @Override
    public StockReservationResult release(ReleaseStockReservationCommand command) {
        Objects.requireNonNull(command, "release stock reservation command is required");
        StockReservationResult result = executeWithLockRetry(
                "release",
                command.reservationId(),
                null,
                () -> lifecycleTransactionService.release(command, Instant.now()));
        recordOutcome("release", "success");
        return result;
    }

    boolean expireIfDue(UUID reservationId, Instant cutoff) {
        Objects.requireNonNull(cutoff, "expiration cutoff is required");
        boolean expired = executeWithLockRetry(
                "expire",
                Objects.requireNonNull(reservationId, "reservation id is required"),
                null,
                () -> lifecycleTransactionService.expireIfDue(reservationId, cutoff));
        recordOutcome("expire", expired ? "success" : "skipped");
        return expired;
    }

    private <T> T executeWithLockRetry(String operation, UUID reservationId, UUID stockItemId, Supplier<T> action) {
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                return action.get();
            } catch (PessimisticLockingFailureException exception) {
                if (attempt == properties.getMaxAttempts()) {
                    recordOutcome(operation, "lock_retry_exhausted");
                    log.warn(
                            "Inventory reservation lock retry exhausted: operation={}, reservationId={}, stockItemId={}, attempts={}",
                            operation,
                            reservationId,
                            stockItemId,
                            attempt);
                    throw new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE);
                }
                meterRegistry.counter(RETRY_METRIC, "operation", operation).increment();
                pauseBeforeRetry(operation);
            } catch (AppException exception) {
                recordOutcome(operation, outcomeFor(exception.getErrorCode()));
                throw exception;
            }
        }
        throw new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE);
    }

    private void validateExpiration(Instant expiresAt, Instant issuedAt) {
        Duration duration = Duration.between(issuedAt, expiresAt);
        if (duration.isZero() || duration.isNegative() || duration.compareTo(properties.getMaxDuration()) > 0) {
            recordOutcome("reserve", "invalid_expiration");
            throw new AppException(ErrorCode.INVENTORY_RESERVATION_EXPIRATION_INVALID);
        }
    }

    private void pauseBeforeRetry(String operation) {
        long backoffMillis = properties.getRetryBackoff().toMillis();
        if (backoffMillis == 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordOutcome(operation, "retry_interrupted");
            throw new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE);
        }
    }

    private String outcomeFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVENTORY_INSUFFICIENT_STOCK -> "insufficient_stock";
            case STOCK_RESERVATION_ALREADY_EXISTS -> "reservation_conflict";
            case STOCK_RESERVATION_NOT_FOUND -> "reservation_not_found";
            case STOCK_RESERVATION_STATE_INVALID -> "invalid_state";
            case STOCK_RESERVATION_EXPIRED -> "expired";
            case STOCK_ITEM_NOT_FOUND -> "stock_item_not_found";
            default -> "rejected";
        };
    }

    private void recordOutcome(String operation, String outcome) {
        meterRegistry
                .counter(OUTCOME_METRIC, "operation", operation, "outcome", outcome)
                .increment();
    }
}
