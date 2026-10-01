package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockMovementRepository;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationOperations;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StockReservationIntegrationTests {

    @Autowired
    private StockReservationOperations reservationOperations;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanCommittedReservationData() {
        jdbcTemplate.update("DELETE FROM ton_kho_giu_hang");
        jdbcTemplate.update("DELETE FROM ton_kho_bien_dong");
        jdbcTemplate.update("DELETE FROM ton_kho_mat_hang");
    }

    @Test
    void reservesAvailableStockAndWritesReservationAndMovementAtomically() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "RESERVE-SUCCESS-01", "WAREHOUSE_01", 10));
        UUID reservationId = UUID.randomUUID();

        var result = reservationOperations.reserve(new ReserveStockCommand(
                reservationId, stockItem.getId(), 4, Instant.now().plusSeconds(300)));

        assertThat(result.status()).isEqualTo(StockReservationStatus.RESERVED);
        assertThat(result.onHand()).isEqualTo(10);
        assertThat(result.reserved()).isEqualTo(4);
        assertThat(result.available()).isEqualTo(6);
        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getReserved()).isEqualTo(4);
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(stockReservationRepository
                        .findById(reservationId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StockReservationStatus.RESERVED);
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.getMovementType()).isEqualTo(StockMovementType.RESERVATION);
                    assertThat(movement.getReservedDelta()).isEqualTo(4);
                    assertThat(movement.getReservedAfter()).isEqualTo(4);
                    assertThat(movement.getReferenceId()).isEqualTo(reservationId.toString());
                });
    }

    @Test
    void rollsBackAllReservationDataWhenAvailableStockIsInsufficient() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "RESERVE-INSUFFICIENT-01", "WAREHOUSE_02", 2));
        UUID reservationId = UUID.randomUUID();

        assertThatThrownBy(() -> reservationOperations.reserve(new ReserveStockCommand(
                        reservationId, stockItem.getId(), 3, Instant.now().plusSeconds(300))))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVENTORY_INSUFFICIENT_STOCK));

        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getReserved()).isZero();
        assertThat(reloaded.getVersion()).isZero();
        assertThat(stockReservationRepository.findById(reservationId)).isEmpty();
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void rejectsDuplicateReservationWithoutChangingBalanceTwice() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "RESERVE-DUPLICATE-01", "WAREHOUSE_03", 5));
        UUID reservationId = UUID.randomUUID();
        ReserveStockCommand command = new ReserveStockCommand(
                reservationId, stockItem.getId(), 2, Instant.now().plusSeconds(300));
        reservationOperations.reserve(command);

        assertThatThrownBy(() -> reservationOperations.reserve(command))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STOCK_RESERVATION_ALREADY_EXISTS));

        assertThat(stockItemRepository.findById(stockItem.getId()).orElseThrow().getReserved())
                .isEqualTo(2);
        assertThat(stockReservationRepository.countByStockItemId(stockItem.getId()))
                .isEqualTo(1);
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
    }
}
