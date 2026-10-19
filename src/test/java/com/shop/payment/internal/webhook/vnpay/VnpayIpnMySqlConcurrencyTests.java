package com.shop.payment.internal.webhook.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.payment.event.PaymentStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class VnpayIpnMySqlConcurrencyTests extends VnpayIntegrationSupport {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.46")
            .withDatabaseName("vnpay_sandbox_test")
            .withUsername("vnpay_test")
            .withPassword("vnpay-test-only-password");

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void eightConcurrentDuplicateNotificationsCommitOnlyOneTransitionAndInbox() throws Exception {
        var attempt = seed();
        var payload = signed(callback(attempt));
        long before = inbox.count();
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("race start timeout");
                    return ipnService.receive(payload).code();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> receipts = new ArrayList<>();
            for (var future : futures) receipts.add(future.get(20, TimeUnit.SECONDS));
            assertThat(receipts).containsOnly("00", "02");
            assertThat(receipts.stream().filter("00"::equals).count()).isEqualTo(1);
        }
        var stored = attempts.findById(attempt.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(stored.getVersion()).isEqualTo(attempt.getVersion() + 1);
        assertThat(inbox.count()).isEqualTo(before + 1);
    }
}
