package com.shop.inventory.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InventoryReservationContractTests {

    @Test
    void normalizesExpirationToTheDatabaseMicrosecondPrecision() {
        Instant expiration = Instant.parse("2026-10-01T10:15:30.123456789Z");

        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), 1, expiration);

        assertThat(command.expiresAt()).isEqualTo(Instant.parse("2026-10-01T10:15:30.123456Z"));
    }

    @Test
    void rejectsNonPositiveReservationQuantity() {
        assertThatThrownBy(() -> new ReserveStockCommand(
                        UUID.randomUUID(), UUID.randomUUID(), 0, Instant.now().plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reservation quantity must be positive");
    }

    @Test
    void rejectsInconsistentResultBalances() {
        assertThatThrownBy(() -> new StockReservationResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        StockReservationStatus.RESERVED,
                        Instant.now().plusSeconds(60),
                        5,
                        2,
                        4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reservation result contains invalid quantities");
    }

    @Test
    void rejectsLifecycleCommandsWithoutReservationId() {
        assertThatThrownBy(() -> new ConfirmStockReservationCommand(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reservation id is required");
        assertThatThrownBy(() -> new ReleaseStockReservationCommand(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reservation id is required");
    }

    @Test
    void exposesPayloadReuseAsAConflictForStandardApiErrorMapping() {
        assertThat(ErrorCode.STOCK_RESERVATION_IDEMPOTENCY_CONFLICT.getCode()).isEqualTo(1225);
        assertThat(ErrorCode.STOCK_RESERVATION_IDEMPOTENCY_CONFLICT.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
