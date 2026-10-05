package com.shop.order.internal.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.event.PaymentStatusChangedEvent;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderPaymentEventMySqlConcurrencyTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_order_payment_event_test")
            .withUsername("shop_order_payment_event_test")
            .withPassword("shop-order-payment-event-test-password");

    @Autowired
    private OrderPaymentEventCoordinator coordinator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
    }

    @Test
    void concurrentDuplicateEventsCreateOneInboxRecordWithoutLeakingAnException() throws Exception {
        PaymentStatusChangedEvent event = succeededEvent();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Void>> deliveries;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> first = executor.submit(() -> deliver(event, ready, start));
            Future<Void> second = executor.submit(() -> deliver(event, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            deliveries = List.of(first, second);
            for (Future<Void> delivery : deliveries) {
                delivery.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM don_hang_su_kien_thanh_toan WHERE event_id = ?",
                        Integer.class,
                        toBytes(event.eventId())))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT outcome FROM don_hang_su_kien_thanh_toan WHERE event_id = ?",
                        String.class,
                        toBytes(event.eventId())))
                .isEqualTo("MANUAL_ACTION_REQUIRED");
    }

    private Void deliver(PaymentStatusChangedEvent event, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent payment event start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent payment event start", exception);
        }
        coordinator.handle(event);
        return null;
    }

    private PaymentStatusChangedEvent succeededEvent() {
        return new PaymentStatusChangedEvent(
                PaymentStatusChangedEvent.CURRENT_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentStatus.PENDING,
                PaymentStatus.SUCCEEDED,
                new BigDecimal("199000.00"),
                "VND",
                "STRIPE",
                "cs_test_order_payment_race",
                null,
                Instant.now());
    }

    private byte[] toBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
