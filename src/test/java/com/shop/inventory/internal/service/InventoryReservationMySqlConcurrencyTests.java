package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.entity.StockMovement;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
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

    private static final int OVERSELL_ROUNDS = 5;
    private static final int OVERSELL_WORKERS = 24;
    private static final int CONCURRENT_TIMEOUT_SECONDS = 30;
    private static final long INITIAL_OVERSELL_STOCK = 60;

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

    @RepeatedTest(
            value = OVERSELL_ROUNDS,
            name = "MySQL oversell invariant round {currentRepetition}/{totalRepetitions}")
    void neverOversellsAcrossConcurrentReserveConfirmAndRelease(RepetitionInfo repetitionInfo) throws Exception {
        int round = repetitionInfo.getCurrentRepetition();
        StockItem stockItem = stockItemRepository.saveAndFlush(StockItem.create(
                deterministicId(round, -1),
                String.format("MYSQL-OVERSELL-%02d", round),
                "WAREHOUSE_STRESS",
                INITIAL_OVERSELL_STOCK));
        List<ReservationRequest> requests = IntStream.range(0, OVERSELL_WORKERS)
                .mapToObj(index -> new ReservationRequest(deterministicId(round, index), (index % 5) + 2L))
                .toList();
        assertThat(requests.stream().mapToLong(ReservationRequest::quantity).sum())
                .isGreaterThan(INITIAL_OVERSELL_STOCK);

        List<OversellAttempt> attempts = runConcurrentReservations(
                requests, stockItem.getId(), Instant.now().plusSeconds(300));
        List<OversellAttempt> successful = attempts.stream()
                .filter(attempt -> attempt.result() != null)
                .sorted(Comparator.comparing(attempt -> attempt.request().reservationId()))
                .toList();
        List<OversellAttempt> rejected =
                attempts.stream().filter(attempt -> attempt.result() == null).toList();

        assertThat(attempts).hasSize(OVERSELL_WORKERS);
        assertThat(successful).isNotEmpty().allSatisfy(attempt -> {
            assertThat(attempt.errorCode()).isNull();
            assertThat(attempt.result().reservationId())
                    .isEqualTo(attempt.request().reservationId());
            assertThat(attempt.result().quantity()).isEqualTo(attempt.request().quantity());
        });
        assertThat(rejected).isNotEmpty().allSatisfy(attempt -> {
            assertThat(attempt.result()).isNull();
            assertThat(attempt.errorCode()).isEqualTo(ErrorCode.INVENTORY_INSUFFICIENT_STOCK);
        });

        long successfullyReserved = successful.stream()
                .mapToLong(attempt -> attempt.request().quantity())
                .sum();
        StockItem afterReserve = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(successfullyReserved).isLessThanOrEqualTo(INITIAL_OVERSELL_STOCK);
        assertThat(afterReserve.getOnHand()).isEqualTo(INITIAL_OVERSELL_STOCK);
        assertThat(afterReserve.getReserved()).isEqualTo(successfullyReserved);
        assertThat(afterReserve.getAvailable()).isEqualTo(INITIAL_OVERSELL_STOCK - successfullyReserved);
        assertThat(afterReserve.getAvailable()).isNotNegative();
        assertThat(stockReservationRepository.countByStockItemId(stockItem.getId()))
                .isEqualTo(successful.size());
        assertThat(stockMovementRepository.findByStockItemId(stockItem.getId(), PageRequest.of(0, 100)))
                .hasSize(successful.size());
        assertThat(countIdempotencyRecords(attempts)).isEqualTo(successful.size());

        List<LifecyclePlan> lifecyclePlans = IntStream.range(0, successful.size())
                .mapToObj(index -> lifecyclePlan(index, successful.get(index)))
                .toList();
        List<LifecyclePlan> transitions = lifecyclePlans.stream()
                .filter(plan -> plan.targetStatus() != StockReservationStatus.RESERVED)
                .toList();
        List<LifecycleResult> transitionResults = runConcurrentLifecycleTransitions(transitions);

        assertThat(transitionResults).hasSameSizeAs(transitions).allSatisfy(result -> assertThat(result.actualStatus())
                .isEqualTo(result.plan().targetStatus()));
        lifecyclePlans.forEach(plan -> assertThat(stockReservationRepository
                        .findById(plan.reservationId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(plan.targetStatus()));

        long confirmed = quantityByStatus(lifecyclePlans, StockReservationStatus.CONFIRMED);
        long stillReserved = quantityByStatus(lifecyclePlans, StockReservationStatus.RESERVED);
        long released = quantityByStatus(lifecyclePlans, StockReservationStatus.RELEASED);
        StockItem afterLifecycle =
                stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(confirmed + stillReserved + released).isEqualTo(successfullyReserved);
        assertThat(confirmed + stillReserved).isLessThanOrEqualTo(INITIAL_OVERSELL_STOCK);
        assertThat(afterLifecycle.getOnHand()).isEqualTo(INITIAL_OVERSELL_STOCK - confirmed);
        assertThat(afterLifecycle.getReserved()).isEqualTo(stillReserved);
        assertThat(afterLifecycle.getAvailable())
                .isEqualTo(INITIAL_OVERSELL_STOCK - confirmed - stillReserved)
                .isNotNegative();

        List<StockMovement> movements = stockMovementRepository
                .findByStockItemId(stockItem.getId(), PageRequest.of(0, 100))
                .getContent();
        assertThat(movements).hasSize(successful.size() + transitions.size());
        assertThat(movements.stream().mapToLong(StockMovement::getOnHandDelta).sum())
                .isEqualTo(-confirmed);
        assertThat(movements.stream().mapToLong(StockMovement::getReservedDelta).sum())
                .isEqualTo(stillReserved);
        assertThat(countIdempotencyRecords(successful)).isEqualTo(successful.size() + transitions.size());
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

    private List<OversellAttempt> runConcurrentReservations(
            List<ReservationRequest> requests, UUID stockItemId, Instant expiresAt) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.size())) {
            List<Future<OversellAttempt>> futures = new ArrayList<>();
            for (ReservationRequest request : requests) {
                futures.add(executor.submit(() -> reserveUnderLoad(request, stockItemId, expiresAt, ready, start)));
            }
            assertThat(ready.await(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
            start.countDown();
            List<OversellAttempt> attempts = new ArrayList<>();
            for (Future<OversellAttempt> future : futures) {
                attempts.add(future.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return List.copyOf(attempts);
        }
    }

    private OversellAttempt reserveUnderLoad(
            ReservationRequest request,
            UUID stockItemId,
            Instant expiresAt,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            StockReservationResult result = reservationOperations.reserve(
                    new ReserveStockCommand(request.reservationId(), stockItemId, request.quantity(), expiresAt));
            return new OversellAttempt(request, result, null);
        } catch (AppException exception) {
            return new OversellAttempt(request, null, exception.getErrorCode());
        }
    }

    private List<LifecycleResult> runConcurrentLifecycleTransitions(List<LifecyclePlan> plans) throws Exception {
        CountDownLatch ready = new CountDownLatch(plans.size());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(plans.size())) {
            List<Future<LifecycleResult>> futures = new ArrayList<>();
            for (LifecyclePlan plan : plans) {
                futures.add(executor.submit(() -> executeLifecycleTransition(plan, ready, start)));
            }
            assertThat(ready.await(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
            start.countDown();
            List<LifecycleResult> results = new ArrayList<>();
            for (Future<LifecycleResult> future : futures) {
                results.add(future.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return List.copyOf(results);
        }
    }

    private LifecycleResult executeLifecycleTransition(LifecyclePlan plan, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        StockReservationResult result =
                switch (plan.targetStatus()) {
                    case CONFIRMED ->
                        reservationOperations.confirm(new ConfirmStockReservationCommand(plan.reservationId()));
                    case RELEASED ->
                        reservationOperations.release(new ReleaseStockReservationCommand(plan.reservationId()));
                    default -> throw new IllegalArgumentException("unsupported concurrent lifecycle target");
                };
        return new LifecycleResult(plan, result.status());
    }

    private LifecyclePlan lifecyclePlan(int index, OversellAttempt attempt) {
        StockReservationStatus targetStatus =
                switch (index % 3) {
                    case 0 -> StockReservationStatus.CONFIRMED;
                    case 1 -> StockReservationStatus.RELEASED;
                    default -> StockReservationStatus.RESERVED;
                };
        return new LifecyclePlan(
                attempt.request().reservationId(), attempt.request().quantity(), targetStatus);
    }

    private long quantityByStatus(List<LifecyclePlan> plans, StockReservationStatus status) {
        return plans.stream()
                .filter(plan -> plan.targetStatus() == status)
                .mapToLong(LifecyclePlan::quantity)
                .sum();
    }

    private long countIdempotencyRecords(List<OversellAttempt> attempts) {
        return attempts.stream()
                .map(OversellAttempt::request)
                .mapToLong(request -> idempotencyRepository.countByReservationId(request.reservationId()))
                .sum();
    }

    private UUID deterministicId(int round, int index) {
        return UUID.nameUUIDFromBytes(("m3.5-oversell-" + round + "-" + index).getBytes(StandardCharsets.UTF_8));
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

    private record ReservationRequest(UUID reservationId, long quantity) {}

    private record OversellAttempt(ReservationRequest request, StockReservationResult result, ErrorCode errorCode) {}

    private record LifecyclePlan(UUID reservationId, long quantity, StockReservationStatus targetStatus) {}

    private record LifecycleResult(LifecyclePlan plan, StockReservationStatus actualStatus) {}
}
