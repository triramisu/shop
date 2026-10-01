package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockReservation;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockMovementRepository;
import com.shop.inventory.internal.repository.StockReservationIdempotencyRepository;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.ConfirmStockReservationCommand;
import com.shop.inventory.reservation.ReleaseStockReservationCommand;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationOperations;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryReservationMySqlConcurrencyTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_inventory_test")
            .withUsername("shop_inventory_test")
            .withPassword("shop-inventory-test-password");

    @Autowired
    private StockReservationOperations reservationOperations;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockReservationIdempotencyRepository idempotencyRepository;

    @Autowired
    private StockReservationExpirationProcessor expirationProcessor;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
    }

    @Test
    void onlyOneRequestWinsWhenTwoTransactionsCompeteForTheLastUnit() throws Exception {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "MYSQL-LAST-UNIT-01", "WAREHOUSE_MYSQL", 1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<ReservationAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ReservationAttempt> first = executor.submit(() -> reserveLastUnit(stockItem.getId(), ready, start));
            Future<ReservationAttempt> second = executor.submit(() -> reserveLastUnit(stockItem.getId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertThat(attempts).filteredOn(ReservationAttempt::success).hasSize(1);
        assertThat(attempts)
                .filteredOn(attempt -> attempt.errorCode() == ErrorCode.INVENTORY_INSUFFICIENT_STOCK)
                .hasSize(1);
        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getOnHand()).isEqualTo(1);
        assertThat(reloaded.getReserved()).isEqualTo(1);
        assertThat(reloaded.getAvailable()).isZero();
        assertThat(stockReservationRepository.countByStockItemId(stockItem.getId()))
                .isEqualTo(1);
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
    }

    @Test
    void concurrentIdenticalReserveRequestsReplayOneCommittedResult() throws Exception {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "MYSQL-IDEMPOTENT-RESERVE-01", "WAREHOUSE_MYSQL", 10));
        ReserveStockCommand command = new ReserveStockCommand(
                UUID.randomUUID(), stockItem.getId(), 3, Instant.now().plusSeconds(300));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<IdempotentAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<IdempotentAttempt> first = executor.submit(() -> reserve(command, ready, start));
            Future<IdempotentAttempt> second = executor.submit(() -> reserve(command, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertThat(attempts).allSatisfy(attempt -> {
            assertThat(attempt.errorCode()).isNull();
            assertThat(attempt.result()).isNotNull();
        });
        assertThat(attempts.get(0).result()).isEqualTo(attempts.get(1).result());
        assertThat(stockItemRepository.findById(stockItem.getId()).orElseThrow().getReserved())
                .isEqualTo(3);
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
        assertThat(idempotencyRepository.countByReservationId(command.reservationId()))
                .isEqualTo(1);
    }

    @Test
    void concurrentDifferentReservePayloadsProduceOneConflictWithoutDoubleMutation() throws Exception {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "MYSQL-IDEMPOTENT-CONFLICT-01", "WAREHOUSE_MYSQL", 10));
        UUID reservationId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(300);
        ReserveStockCommand firstCommand = new ReserveStockCommand(reservationId, stockItem.getId(), 2, expiresAt);
        ReserveStockCommand secondCommand = new ReserveStockCommand(reservationId, stockItem.getId(), 4, expiresAt);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<IdempotentAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<IdempotentAttempt> first = executor.submit(() -> reserve(firstCommand, ready, start));
            Future<IdempotentAttempt> second = executor.submit(() -> reserve(secondCommand, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertThat(attempts).filteredOn(attempt -> attempt.result() != null).hasSize(1);
        assertThat(attempts)
                .filteredOn(attempt -> attempt.errorCode() == ErrorCode.STOCK_RESERVATION_IDEMPOTENCY_CONFLICT)
                .hasSize(1);
        StockReservationResult committed = attempts.stream()
                .map(IdempotentAttempt::result)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
        assertThat(stockItemRepository.findById(stockItem.getId()).orElseThrow().getReserved())
                .isEqualTo(committed.quantity());
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
        assertThat(idempotencyRepository.countByReservationId(reservationId)).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalConfirmRequestsConsumeReservationOnlyOnce() throws Exception {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "MYSQL-IDEMPOTENT-CONFIRM-01", "WAREHOUSE_MYSQL", 6));
        UUID reservationId = UUID.randomUUID();
        reservationOperations.reserve(new ReserveStockCommand(
                reservationId, stockItem.getId(), 2, Instant.now().plusSeconds(300)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<IdempotentAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<IdempotentAttempt> first = executor.submit(() -> confirm(reservationId, ready, start));
            Future<IdempotentAttempt> second = executor.submit(() -> confirm(reservationId, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertThat(attempts)
                .allSatisfy(attempt -> assertThat(attempt.errorCode()).isNull());
        assertThat(attempts.get(0).result()).isEqualTo(attempts.get(1).result());
        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getOnHand()).isEqualTo(4);
        assertThat(reloaded.getReserved()).isZero();
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .hasSize(2);
        assertThat(idempotencyRepository.countByReservationId(reservationId)).isEqualTo(2);
    }

    @Test
    void onlyOneTerminalTransitionWinsWhenConfirmAndReleaseCompete() throws Exception {
        StockItem stockItem = stockItemRepository.saveAndFlush(
                StockItem.create(UUID.randomUUID(), "MYSQL-TERMINAL-RACE-01", "WAREHOUSE_MYSQL", 5));
        UUID reservationId = UUID.randomUUID();
        reservationOperations.reserve(new ReserveStockCommand(
                reservationId, stockItem.getId(), 2, Instant.now().plusSeconds(300)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<LifecycleAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LifecycleAttempt> confirmation =
                    executor.submit(() -> confirmReservation(reservationId, ready, start));
            Future<LifecycleAttempt> release = executor.submit(() -> releaseReservation(reservationId, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(confirmation.get(20, TimeUnit.SECONDS), release.get(20, TimeUnit.SECONDS));
        }

        assertThat(attempts).filteredOn(LifecycleAttempt::success).hasSize(1);
        assertThat(attempts)
                .filteredOn(attempt -> attempt.errorCode() == ErrorCode.STOCK_RESERVATION_STATE_INVALID)
                .hasSize(1);
        StockReservation reservation =
                stockReservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isIn(StockReservationStatus.CONFIRMED, StockReservationStatus.RELEASED);
        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getReserved()).isZero();
        if (reservation.getStatus() == StockReservationStatus.CONFIRMED) {
            assertThat(reloaded.getOnHand()).isEqualTo(3);
        } else {
            assertThat(reloaded.getOnHand()).isEqualTo(5);
        }
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .hasSize(2);
    }

    @Test
    void concurrentExpirationBatchesReleaseDueReservationOnlyOnce() throws Exception {
        Instant cutoff = Instant.now();
        StockItem stockItem = StockItem.create(UUID.randomUUID(), "MYSQL-EXPIRATION-RACE-01", "WAREHOUSE_MYSQL", 4);
        stockItem.reserve(2);
        stockItem = stockItemRepository.saveAndFlush(stockItem);
        UUID reservationId = UUID.randomUUID();
        stockReservationRepository.saveAndFlush(
                StockReservation.issue(reservationId, stockItem, 2, cutoff.minusSeconds(1), cutoff.minusSeconds(60)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<StockReservationExpirationProcessor.ExpirationBatchResult> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<StockReservationExpirationProcessor.ExpirationBatchResult> first =
                    executor.submit(() -> expireBatch(cutoff, ready, start));
            Future<StockReservationExpirationProcessor.ExpirationBatchResult> second =
                    executor.submit(() -> expireBatch(cutoff, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertThat(results.stream()
                        .mapToInt(StockReservationExpirationProcessor.ExpirationBatchResult::expired)
                        .sum())
                .isEqualTo(1);
        assertThat(stockReservationRepository
                        .findById(reservationId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StockReservationStatus.EXPIRED);
        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getOnHand()).isEqualTo(4);
        assertThat(reloaded.getReserved()).isZero();
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
    }

    private ReservationAttempt reserveLastUnit(UUID stockItemId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            reservationOperations.reserve(new ReserveStockCommand(
                    UUID.randomUUID(), stockItemId, 1, Instant.now().plusSeconds(300)));
            return new ReservationAttempt(true, null);
        } catch (AppException exception) {
            return new ReservationAttempt(false, exception.getErrorCode());
        }
    }

    private IdempotentAttempt reserve(ReserveStockCommand command, CountDownLatch ready, CountDownLatch start) {
        return runIdempotentAttempt(() -> reservationOperations.reserve(command), ready, start);
    }

    private IdempotentAttempt confirm(UUID reservationId, CountDownLatch ready, CountDownLatch start) {
        return runIdempotentAttempt(
                () -> reservationOperations.confirm(new ConfirmStockReservationCommand(reservationId)), ready, start);
    }

    private IdempotentAttempt runIdempotentAttempt(
            java.util.concurrent.Callable<StockReservationResult> action, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            return new IdempotentAttempt(action.call(), null);
        } catch (AppException exception) {
            return new IdempotentAttempt(null, exception.getErrorCode());
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected idempotent reservation failure", exception);
        }
    }

    private LifecycleAttempt confirmReservation(UUID reservationId, CountDownLatch ready, CountDownLatch start) {
        return runLifecycleAttempt(
                () -> reservationOperations.confirm(new ConfirmStockReservationCommand(reservationId)), ready, start);
    }

    private LifecycleAttempt releaseReservation(UUID reservationId, CountDownLatch ready, CountDownLatch start) {
        return runLifecycleAttempt(
                () -> reservationOperations.release(new ReleaseStockReservationCommand(reservationId)), ready, start);
    }

    private LifecycleAttempt runLifecycleAttempt(Runnable action, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            action.run();
            return new LifecycleAttempt(true, null);
        } catch (AppException exception) {
            return new LifecycleAttempt(false, exception.getErrorCode());
        }
    }

    private StockReservationExpirationProcessor.ExpirationBatchResult expireBatch(
            Instant cutoff, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        return expirationProcessor.expireBatch(cutoff);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent reservation start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent reservation start", exception);
        }
    }

    private record ReservationAttempt(boolean success, ErrorCode errorCode) {}

    private record LifecycleAttempt(boolean success, ErrorCode errorCode) {}

    private record IdempotentAttempt(StockReservationResult result, ErrorCode errorCode) {}
}
