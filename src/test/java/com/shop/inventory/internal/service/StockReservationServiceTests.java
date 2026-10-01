package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.reservation.ConfirmStockReservationCommand;
import com.shop.inventory.reservation.ReleaseStockReservationCommand;
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

    @Mock
    private StockReservationLifecycleTransactionService lifecycleTransactionService;

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
        service =
                new StockReservationService(transactionService, lifecycleTransactionService, properties, meterRegistry);
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
                        .tag("operation", "reserve")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(meterRegistry
                        .get(StockReservationService.OUTCOME_METRIC)
                        .tag("operation", "reserve")
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
                        .tag("operation", "reserve")
                        .counter()
                        .count())
                .isEqualTo(2);
        assertThat(meterRegistry
                        .get(StockReservationService.OUTCOME_METRIC)
                        .tag("operation", "reserve")
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

    @Test
    void confirmsReservationAndRecordsOperationOutcome() {
        ConfirmStockReservationCommand command = new ConfirmStockReservationCommand(UUID.randomUUID());
        StockReservationResult expected = result(command.reservationId(), StockReservationStatus.CONFIRMED, 0, 0);
        when(lifecycleTransactionService.confirm(eq(command), any(Instant.class)))
                .thenReturn(new StockReservationLifecycleExecution(
                        expected, StockReservationLifecycleExecution.Outcome.CONFIRMED));

        assertThat(service.confirm(command)).isEqualTo(expected);
        assertThat(meterRegistry
                        .get(StockReservationService.OUTCOME_METRIC)
                        .tag("operation", "confirm")
                        .tag("outcome", "success")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void commitsExpirationThenRejectsLateConfirmation() {
        ConfirmStockReservationCommand command = new ConfirmStockReservationCommand(UUID.randomUUID());
        StockReservationResult expired = result(command.reservationId(), StockReservationStatus.EXPIRED, 1, 0);
        when(lifecycleTransactionService.confirm(eq(command), any(Instant.class)))
                .thenReturn(new StockReservationLifecycleExecution(
                        expired, StockReservationLifecycleExecution.Outcome.EXPIRED_DURING_CONFIRM));

        assertThatThrownBy(() -> service.confirm(command))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STOCK_RESERVATION_EXPIRED));
        assertThat(meterRegistry
                        .get(StockReservationService.OUTCOME_METRIC)
                        .tag("operation", "confirm")
                        .tag("outcome", "expired")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void releasesReservationWithoutUsingReserveTransactionService() {
        ReleaseStockReservationCommand command = new ReleaseStockReservationCommand(UUID.randomUUID());
        StockReservationResult expected = result(command.reservationId(), StockReservationStatus.RELEASED, 1, 0);
        when(lifecycleTransactionService.release(eq(command), any(Instant.class)))
                .thenReturn(expected);

        assertThat(service.release(command)).isEqualTo(expected);
        verify(transactionService, never()).reserve(any(), any());
    }

    private ReserveStockCommand command(Duration ttl) {
        return new ReserveStockCommand(
                UUID.randomUUID(), UUID.randomUUID(), 1, Instant.now().plus(ttl));
    }

    private StockReservationResult result(ReserveStockCommand command) {
        return result(command.reservationId(), StockReservationStatus.RESERVED, 1, 1);
    }

    private StockReservationResult result(
            UUID reservationId, StockReservationStatus status, long onHand, long reserved) {
        return new StockReservationResult(
                reservationId,
                UUID.randomUUID(),
                1,
                status,
                Instant.now().plusSeconds(60),
                onHand,
                reserved,
                onHand - reserved);
    }
}
