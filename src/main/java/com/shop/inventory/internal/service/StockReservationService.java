package com.shop.inventory.internal.service;

import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationOperations;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
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
    InventoryReservationProperties properties;
    MeterRegistry meterRegistry;

    @Override
    public StockReservationResult reserve(ReserveStockCommand command) {
        Objects.requireNonNull(command, "reserve stock command is required");
        Instant issuedAt = Instant.now();
        validateExpiration(command.expiresAt(), issuedAt);

        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                StockReservationResult result = transactionService.reserve(command, issuedAt);
                recordOutcome("success");
                return result;
            } catch (PessimisticLockingFailureException exception) {
                if (attempt == properties.getMaxAttempts()) {
                    recordOutcome("lock_retry_exhausted");
                    log.warn(
                            "Inventory reservation lock retry exhausted: reservationId={}, stockItemId={}, attempts={}",
                            command.reservationId(),
                            command.stockItemId(),
                            attempt);
                    throw new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE);
                }
                meterRegistry.counter(RETRY_METRIC).increment();
                pauseBeforeRetry();
            } catch (AppException exception) {
                recordOutcome(outcomeFor(exception.getErrorCode()));
                throw exception;
            }
        }
        throw new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE);
    }

    private void validateExpiration(Instant expiresAt, Instant issuedAt) {
        Duration duration = Duration.between(issuedAt, expiresAt);
        if (duration.isZero() || duration.isNegative() || duration.compareTo(properties.getMaxDuration()) > 0) {
            recordOutcome("invalid_expiration");
            throw new AppException(ErrorCode.INVENTORY_RESERVATION_EXPIRATION_INVALID);
        }
    }

    private void pauseBeforeRetry() {
        long backoffMillis = properties.getRetryBackoff().toMillis();
        if (backoffMillis == 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordOutcome("retry_interrupted");
            throw new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE);
        }
    }

    private String outcomeFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVENTORY_INSUFFICIENT_STOCK -> "insufficient_stock";
            case STOCK_RESERVATION_ALREADY_EXISTS -> "reservation_conflict";
            case STOCK_ITEM_NOT_FOUND -> "stock_item_not_found";
            default -> "rejected";
        };
    }

    private void recordOutcome(String outcome) {
        meterRegistry.counter(OUTCOME_METRIC, "outcome", outcome).increment();
    }
}
