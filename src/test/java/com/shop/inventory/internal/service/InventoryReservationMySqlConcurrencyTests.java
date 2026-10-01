package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.inventory.internal.entity.StockItem;
import com.shop.inventory.internal.repository.StockItemRepository;
import com.shop.inventory.internal.repository.StockMovementRepository;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationOperations;
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
}
