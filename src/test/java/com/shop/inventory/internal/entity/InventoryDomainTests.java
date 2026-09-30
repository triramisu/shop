package com.shop.inventory.internal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.inventory.event.StockMovementType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryDomainTests {

    @Test
    void normalizesIdentifiersAndDerivesAvailableQuantity() {
        StockItem stockItem = StockItem.create(UUID.randomUUID(), " sku.demo-01 ", " warehouse_01 ", 12);

        assertThat(stockItem.getSku()).isEqualTo("SKU.DEMO-01");
        assertThat(stockItem.getLocationCode()).isEqualTo("WAREHOUSE_01");
        assertThat(stockItem.getOnHand()).isEqualTo(12);
        assertThat(stockItem.getReserved()).isZero();
        assertThat(stockItem.getAvailable()).isEqualTo(12);
    }

    @Test
    void preservesBalanceInvariantsAcrossReserveReleaseConfirmAndAdjustment() {
        StockItem stockItem = stockItem(10);

        stockItem.reserve(4);
        assertThat(stockItem.getReserved()).isEqualTo(4);
        assertThat(stockItem.getAvailable()).isEqualTo(6);

        stockItem.release(1);
        stockItem.confirm(2);
        stockItem.adjustOnHand(3);

        assertThat(stockItem.getOnHand()).isEqualTo(11);
        assertThat(stockItem.getReserved()).isEqualTo(1);
        assertThat(stockItem.getAvailable()).isEqualTo(10);
    }

    @Test
    void rejectsNegativeOverflowZeroAndInsufficientQuantities() {
        assertThatThrownBy(() -> stockItem(-1)).isInstanceOf(IllegalArgumentException.class);

        StockItem stockItem = stockItem(5);
        assertThatThrownBy(() -> stockItem.adjustOnHand(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stockItem.adjustOnHand(-6)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> stockItem.reserve(6)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> stockItem.reserve(0)).isInstanceOf(IllegalArgumentException.class);

        StockItem maximum = stockItem(Long.MAX_VALUE);
        assertThatThrownBy(() -> maximum.adjustOnHand(1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsImmutableMovementWithTheResultingBalance() {
        StockItem stockItem = stockItem(8);
        stockItem.reserve(3);
        Instant occurredAt = Instant.parse("2026-09-30T10:15:30Z");

        StockMovement movement = StockMovement.record(
                stockItem, StockMovementType.RESERVATION, 0, 3, "Giữ cho đơn hàng", "ORDER-001", occurredAt);

        assertThat(movement.getOnHandDelta()).isZero();
        assertThat(movement.getReservedDelta()).isEqualTo(3);
        assertThat(movement.getOnHandAfter()).isEqualTo(8);
        assertThat(movement.getReservedAfter()).isEqualTo(3);
        assertThat(movement.getOccurredAt()).isEqualTo(occurredAt);
        assertThatThrownBy(() -> StockMovement.record(
                        stockItem, StockMovementType.ADJUSTMENT, 0, 0, "Không đổi", null, occurredAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issuesReservationOnlyWithPositiveQuantityAndFutureExpiration() {
        StockItem stockItem = stockItem(10);
        Instant issuedAt = Instant.parse("2026-09-30T10:15:30Z");
        UUID reservationId = UUID.randomUUID();

        StockReservation reservation =
                StockReservation.issue(reservationId, stockItem, 2, issuedAt.plusSeconds(300), issuedAt);

        assertThat(reservation.getId()).isEqualTo(reservationId);
        assertThat(reservation.getQuantity()).isEqualTo(2);
        assertThat(reservation.getStatus()).isEqualTo(StockReservationStatus.RESERVED);
        assertThatThrownBy(() ->
                        StockReservation.issue(UUID.randomUUID(), stockItem, 0, issuedAt.plusSeconds(300), issuedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockReservation.issue(UUID.randomUUID(), stockItem, 1, issuedAt, issuedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StockItem stockItem(long quantity) {
        return StockItem.create(UUID.randomUUID(), "DOMAIN-SKU-01", "WAREHOUSE_01", quantity);
    }
}
