package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTests {

    @Mock
    private StockReservationTransactionService transactionService;

    private InventoryReservationProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private StockReservationService service;

    @BeforeEach
    void setUp() {
        properties = new InventoryReservationProperties();
        properties.setMaxAttempts(3);
        properties.setMaxDuration(Duration.ofMinutes(30));
        properties.setRetryBackoff(Duration.ZERO);
        meterRegistry = new SimpleMeterRegistry();
        service = new StockReservationService(transactionService, properties, meterRegistry);
    }

    @Test
    void retriesLockFailureThenReturnsSuccess() {
        ReserveStockCommand command = command(Duration.ofMinutes(5));
        StockReservationResult expected = result(command);
        when(transactionService.reserve(eq(command), any(Instant.class)))
                .thenThrow(new CannotAcquireLockException("locked"))
                .thenReturn(expected);

        assertThat(service.reserve(command)).isEqualTo(expected);
        verify(transactionService, org.mockito.Mockito.times(2)).reserve(eq(command), any(Instant.class));
        assertThat(meterRegistry
                        .get(StockReservationService.RETRY_METRIC)
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(meterRegistry
                        .get(StockReservationService.OUTCOME_METRIC)
                        .tag("outcome", "success")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void stopsAfterConfiguredLockAttempts() {
        ReserveStockCommand command = command(Duration.ofMinutes(5));
        when(transactionService.reserve(eq(command), any(Instant.class)))
                .thenThrow(new CannotAcquireLockException("locked"));

        assertThatThrownBy(() -> service.reserve(command))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE));
        verify(transactionService, org.mockito.Mockito.times(3)).reserve(eq(command), any(Instant.class));
        assertThat(meterRegistry
                        .get(StockReservationService.RETRY_METRIC)
                        .counter()
                        .count())
                .isEqualTo(2);
        assertThat(meterRegistry
                        .get(StockReservationService.OUTCOME_METRIC)
                        .tag("outcome", "lock_retry_exhausted")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void doesNotRetryBusinessRejection() {
        ReserveStockCommand command = command(Duration.ofMinutes(5));
        when(transactionService.reserve(eq(command), any(Instant.class)))
                .thenThrow(new AppException(ErrorCode.INVENTORY_INSUFFICIENT_STOCK));

        assertThatThrownBy(() -> service.reserve(command))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVENTORY_INSUFFICIENT_STOCK));
        verify(transactionService).reserve(eq(command), any(Instant.class));
    }

    @Test
    void rejectsExpirationOutsideConfiguredWindowBeforeStartingTransaction() {
        ReserveStockCommand command = command(Duration.ofHours(1));

        assertThatThrownBy(() -> service.reserve(command))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVENTORY_RESERVATION_EXPIRATION_INVALID));
        verify(transactionService, never()).reserve(any(), any());
    }

    private ReserveStockCommand command(Duration ttl) {
        return new ReserveStockCommand(
                UUID.randomUUID(), UUID.randomUUID(), 1, Instant.now().plus(ttl));
    }

    private StockReservationResult result(ReserveStockCommand command) {
        return new StockReservationResult(
                command.reservationId(),
                command.stockItemId(),
                command.quantity(),
                StockReservationStatus.RESERVED,
                command.expiresAt(),
                1,
                1,
                0);
    }
}
