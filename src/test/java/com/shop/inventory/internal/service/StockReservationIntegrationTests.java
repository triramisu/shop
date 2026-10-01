package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.event.StockReservationStatusChangedEvent;
import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockMovement;
import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockMovementRepository;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.ConfirmStockReservationCommand;
import com.shop.inventory.reservation.ReleaseStockReservationCommand;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationOperations;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
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

    @Autowired
    private StockReservationExpirationProcessor expirationProcessor;

    @Autowired
    private ApplicationEvents applicationEvents;

    @AfterEach
    void cleanCommittedReservationData() {
        jdbcTemplate.update("DELETE FROM ton_kho_yeu_cau_luy_dang");
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
    void rejectsANewExpiredReservationBeforeChangingStock() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "RESERVE-EXPIRED-01", "WAREHOUSE_02", 2));
        UUID reservationId = UUID.randomUUID();

        assertThatThrownBy(() -> reservationOperations.reserve(new ReserveStockCommand(
                        reservationId, stockItem.getId(), 1, Instant.now().minusSeconds(1))))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVENTORY_RESERVATION_EXPIRATION_INVALID));

        assertThat(stockItemRepository.findById(stockItem.getId()).orElseThrow().getReserved())
                .isZero();
        assertThat(stockReservationRepository.findById(reservationId)).isEmpty();
        assertThat(movements(stockItem.getId())).isEmpty();
    }

    @Test
    void replaysDuplicateReservationWithoutChangingBalanceTwice() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "RESERVE-DUPLICATE-01", "WAREHOUSE_03", 5));
        UUID reservationId = UUID.randomUUID();
        ReserveStockCommand command = new ReserveStockCommand(
                reservationId, stockItem.getId(), 2, Instant.now().plusSeconds(300));
        var firstResult = reservationOperations.reserve(command);

        var replayedResult = reservationOperations.reserve(command);

        assertThat(replayedResult).isEqualTo(firstResult);
        assertThat(stockItemRepository.findById(stockItem.getId()).orElseThrow().getReserved())
                .isEqualTo(2);
        assertThat(stockReservationRepository.countByStockItemId(stockItem.getId()))
                .isEqualTo(1);
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ton_kho_yeu_cau_luy_dang WHERE reservation_id = ?",
                        Long.class,
                        reservationId))
                .isEqualTo(1L);
        Map<String, Object> replayRecord = jdbcTemplate.queryForMap("""
                SELECT operation, processing_status, result_status, result_quantity,
                       result_on_hand, result_reserved, CHAR_LENGTH(request_fingerprint) AS fingerprint_length
                  FROM ton_kho_yeu_cau_luy_dang
                 WHERE reservation_id = ?
                """, reservationId);
        assertThat(replayRecord)
                .containsEntry("operation", "RESERVE")
                .containsEntry("processing_status", "COMPLETED")
                .containsEntry("result_status", "RESERVED")
                .containsEntry("result_quantity", 2L)
                .containsEntry("result_on_hand", 5L)
                .containsEntry("result_reserved", 2L)
                .containsEntry("fingerprint_length", 64L);
    }

    @Test
    void rejectsAReusedReservationIdWithDifferentPayload() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "RESERVE-CONFLICT-01", "WAREHOUSE_03", 5));
        UUID reservationId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(300).truncatedTo(ChronoUnit.MICROS);
        reservationOperations.reserve(new ReserveStockCommand(reservationId, stockItem.getId(), 2, expiresAt));

        assertThatThrownBy(() -> reservationOperations.reserve(
                        new ReserveStockCommand(reservationId, stockItem.getId(), 3, expiresAt)))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STOCK_RESERVATION_IDEMPOTENCY_CONFLICT));

        assertThat(stockItemRepository.findById(stockItem.getId()).orElseThrow().getReserved())
                .isEqualTo(2);
        assertThat(movements(stockItem.getId())).hasSize(1);
    }

    @Test
    void lazilyBuildsAReplayRecordForAReservationCreatedBeforeV14() {
        Instant issuedAt = Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        StockItem stockItem = StockItem.create(UUID.randomUUID(), "RESERVE-LEGACY-01", "WAREHOUSE_03", 5);
        stockItem.reserve(2);
        stockItem = stockItemRepository.saveAndFlush(stockItem);
        UUID reservationId = UUID.randomUUID();
        stockReservationRepository.saveAndFlush(
                StockReservation.issue(reservationId, stockItem, 2, expiresAt, issuedAt));
        stockMovementRepository.saveAndFlush(StockMovement.record(
                stockItem, StockMovementType.RESERVATION, 0, 2, "Giữ tồn kho", reservationId.toString(), issuedAt));
        ReserveStockCommand command = new ReserveStockCommand(reservationId, stockItem.getId(), 2, expiresAt);

        var replayedResult = reservationOperations.reserve(command);

        assertThat(replayedResult.status()).isEqualTo(StockReservationStatus.RESERVED);
        assertThat(replayedResult.reserved()).isEqualTo(2);
        assertThat(movements(stockItem.getId())).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ton_kho_yeu_cau_luy_dang WHERE reservation_id = ?",
                        Long.class,
                        reservationId))
                .isEqualTo(1L);
    }

    @Test
    void confirmsReservationAndAtomicallyConsumesOnHandAndReservedBalances() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "CONFIRM-SUCCESS-01", "WAREHOUSE_04", 10));
        UUID reservationId = UUID.randomUUID();
        reservationOperations.reserve(new ReserveStockCommand(
                reservationId, stockItem.getId(), 4, Instant.now().plusSeconds(300)));

        ConfirmStockReservationCommand command = new ConfirmStockReservationCommand(reservationId);
        var result = reservationOperations.confirm(command);
        var replayedResult = reservationOperations.confirm(command);

        assertThat(replayedResult).isEqualTo(result);
        assertThat(result.status()).isEqualTo(StockReservationStatus.CONFIRMED);
        assertThat(result.onHand()).isEqualTo(6);
        assertThat(result.reserved()).isZero();
        assertThat(result.available()).isEqualTo(6);
        assertThat(stockReservationRepository
                        .findById(reservationId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StockReservationStatus.CONFIRMED);
        assertThat(movements(stockItem.getId()))
                .extracting(movement -> movement.getMovementType())
                .containsExactly(StockMovementType.RESERVATION, StockMovementType.CONFIRMATION);
        assertThat(movements(stockItem.getId()).get(1)).satisfies(movement -> {
            assertThat(movement.getOnHandDelta()).isEqualTo(-4);
            assertThat(movement.getReservedDelta()).isEqualTo(-4);
            assertThat(movement.getOnHandAfter()).isEqualTo(6);
            assertThat(movement.getReservedAfter()).isZero();
        });
        assertThat(applicationEvents.stream(StockReservationStatusChangedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.reservationId()).isEqualTo(reservationId);
                    assertThat(event.previousStatus()).isEqualTo(StockReservationStatus.RESERVED);
                    assertThat(event.currentStatus()).isEqualTo(StockReservationStatus.CONFIRMED);
                });
    }

    @Test
    void releasesReservationOnceAndRejectsAnyLaterTerminalTransition() {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "RELEASE-SUCCESS-01", "WAREHOUSE_05", 8));
        UUID reservationId = UUID.randomUUID();
        reservationOperations.reserve(new ReserveStockCommand(
                reservationId, stockItem.getId(), 3, Instant.now().plusSeconds(300)));

        ReleaseStockReservationCommand command = new ReleaseStockReservationCommand(reservationId);
        var result = reservationOperations.release(command);
        var replayedResult = reservationOperations.release(command);

        assertThat(replayedResult).isEqualTo(result);
        assertThat(result.status()).isEqualTo(StockReservationStatus.RELEASED);
        assertThat(result.onHand()).isEqualTo(8);
        assertThat(result.reserved()).isZero();
        assertThatThrownBy(() -> reservationOperations.confirm(new ConfirmStockReservationCommand(reservationId)))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STOCK_RESERVATION_STATE_INVALID));
        assertThat(movements(stockItem.getId()))
                .extracting(movement -> movement.getMovementType())
                .containsExactly(StockMovementType.RESERVATION, StockMovementType.RELEASE);
    }

    @Test
    void expiresReservationInItsOwnTransactionBeforeRejectingLateConfirmation() {
        Instant now = Instant.now();
        StockItem stockItem = createReservedStock("CONFIRM-EXPIRED-01", "WAREHOUSE_06", 7, 2);
        UUID reservationId = UUID.randomUUID();
        stockReservationRepository.saveAndFlush(
                StockReservation.issue(reservationId, stockItem, 2, now.minusSeconds(60), now.minusSeconds(120)));

        assertThatThrownBy(() -> reservationOperations.confirm(new ConfirmStockReservationCommand(reservationId)))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STOCK_RESERVATION_EXPIRED));
        assertThatThrownBy(() -> reservationOperations.confirm(new ConfirmStockReservationCommand(reservationId)))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STOCK_RESERVATION_EXPIRED));

        assertThat(stockReservationRepository
                        .findById(reservationId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StockReservationStatus.EXPIRED);
        assertThat(stockItemRepository.findById(stockItem.getId()).orElseThrow().getReserved())
                .isZero();
        assertThat(movements(stockItem.getId()))
                .extracting(movement -> movement.getMovementType())
                .containsExactly(StockMovementType.EXPIRATION);
    }

    @Test
    void expirationProcessorSelectsOnlyDueReservationsAndIsSafeToRunAgain() {
        Instant cutoff = Instant.now();
        StockItem dueStock = createReservedStock("EXPIRATION-DUE-01", "WAREHOUSE_07", 5, 1);
        StockItem futureStock = createReservedStock("EXPIRATION-FUTURE-01", "WAREHOUSE_07", 5, 1);
        UUID dueReservationId = UUID.randomUUID();
        UUID futureReservationId = UUID.randomUUID();
        stockReservationRepository.saveAndFlush(
                StockReservation.issue(dueReservationId, dueStock, 1, cutoff.minusSeconds(1), cutoff.minusSeconds(60)));
        stockReservationRepository.saveAndFlush(StockReservation.issue(
                futureReservationId, futureStock, 1, cutoff.plusSeconds(60), cutoff.minusSeconds(60)));

        var firstRun = expirationProcessor.expireBatch(cutoff);
        var secondRun = expirationProcessor.expireBatch(cutoff);

        assertThat(firstRun.selected()).isEqualTo(1);
        assertThat(firstRun.expired()).isEqualTo(1);
        assertThat(secondRun.selected()).isZero();
        assertThat(stockReservationRepository
                        .findById(dueReservationId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StockReservationStatus.EXPIRED);
        assertThat(stockReservationRepository
                        .findById(futureReservationId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StockReservationStatus.RESERVED);
        assertThat(stockItemRepository.findById(dueStock.getId()).orElseThrow().getReserved())
                .isZero();
        assertThat(stockItemRepository
                        .findById(futureStock.getId())
                        .orElseThrow()
                        .getReserved())
                .isEqualTo(1);
    }

    private StockItem createReservedStock(String sku, String locationCode, long onHand, long reserved) {
        StockItem stockItem = StockItem.create(UUID.randomUUID(), sku, locationCode, onHand);
        stockItem.reserve(reserved);
        return stockItemRepository.saveAndFlush(stockItem);
    }

    private List<StockMovement> movements(UUID stockItemId) {
        return stockMovementRepository
                .findByStockItemId(stockItemId, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "occurredAt")))
                .getContent();
    }
}
