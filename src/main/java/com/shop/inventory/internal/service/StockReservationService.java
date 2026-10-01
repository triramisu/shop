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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            StockReservationResult result = executeWithLockRetry(
                    "reserve",
                    command.reservationId(),
                    command.stockItemId(),
                    () -> transactionService.reserve(command, issuedAt));
            recordOutcome("reserve", "success");
            return result;
        } catch (AppException exception) {
            if (exception.getErrorCode() == ErrorCode.STOCK_RESERVATION_ALREADY_EXISTS) {
                try {
                    StockReservationResult replay = executeWithLockRetry(
                            "reserve",
                            command.reservationId(),
                            command.stockItemId(),
                            () -> transactionService.replayExisting(command));
                    recordOutcome("reserve", "replayed");
                    return replay;
                } catch (AppException replayFailure) {
                    recordFailure("reserve", replayFailure);
                    throw replayFailure;
                }
            }
            recordFailure("reserve", exception);
            throw exception;
        }
    }

    @Override
    public StockReservationResult confirm(ConfirmStockReservationCommand command) {
        Objects.requireNonNull(command, "confirm stock reservation command is required");
        StockReservationLifecycleExecution execution;
        try {
            execution = executeWithLockRetry(
                    "confirm",
                    command.reservationId(),
                    null,
                    () -> lifecycleTransactionService.confirm(command, Instant.now()));
        } catch (AppException exception) {
            recordFailure("confirm", exception);
            throw exception;
        }
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
        try {
            StockReservationResult result = executeWithLockRetry(
                    "release",
                    command.reservationId(),
                    null,
                    () -> lifecycleTransactionService.release(command, Instant.now()));
            recordOutcome("release", "success");
            return result;
        } catch (AppException exception) {
            recordFailure("release", exception);
            throw exception;
        }
    }

    boolean expireIfDue(UUID reservationId, Instant cutoff) {
        Objects.requireNonNull(cutoff, "expiration cutoff is required");
        try {
            boolean expired = executeWithLockRetry(
                    "expire",
                    Objects.requireNonNull(reservationId, "reservation id is required"),
                    null,
                    () -> lifecycleTransactionService.expireIfDue(reservationId, cutoff));
            recordOutcome("expire", expired ? "success" : "skipped");
            return expired;
        } catch (AppException exception) {
            recordFailure("expire", exception);
            throw exception;
        }
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
            }
        }
        throw new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE);
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
            case INVENTORY_RESERVATION_EXPIRATION_INVALID -> "invalid_expiration";
            case STOCK_RESERVATION_ALREADY_EXISTS -> "reservation_conflict";
            case STOCK_RESERVATION_NOT_FOUND -> "reservation_not_found";
            case STOCK_RESERVATION_STATE_INVALID -> "invalid_state";
            case STOCK_RESERVATION_EXPIRED -> "expired";
            case STOCK_RESERVATION_IDEMPOTENCY_CONFLICT -> "idempotency_conflict";
            case STOCK_RESERVATION_REPLAY_UNAVAILABLE -> "replay_unavailable";
            case STOCK_ITEM_NOT_FOUND -> "stock_item_not_found";
            default -> "rejected";
        };
    }

    private void recordFailure(String operation, AppException exception) {
        if (exception.getErrorCode() != ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE) {
            recordOutcome(operation, outcomeFor(exception.getErrorCode()));
        }
    }

    private void recordOutcome(String operation, String outcome) {
        meterRegistry
                .counter(OUTCOME_METRIC, "operation", operation, "outcome", outcome)
                .increment();
    }
}
