package com.shop.payment.internal.webhook.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.webhook.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.net.URI;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "app.payment.provider.type=stripe",
            "app.payment.stripe.secret-key=sk_test_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "app.payment.stripe.return-state-secret=test-state-key-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "app.payment.stripe.webhook-secrets=whsec_cccccccccccccccccccccccccccccccc",
            "app.payment.stripe.success-url=https://shop.example.com/api/payments/checkout/return",
            "app.payment.stripe.cancel-url=https://shop.example.com/api/payments/checkout/cancel",
            "app.payment.stripe.allowed-return-hosts=shop.example.com"
        })
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StripeWebhookMySqlConcurrencyTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_payment_webhook_test")
            .withUsername("shop_payment_webhook_test")
            .withPassword("shop-payment-webhook-test-password");

    @Autowired
    private StripeWebhookService webhookService;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
    }

    @Test
    void concurrentDuplicateDeliveriesApplyExactlyOnePaymentTransition() throws Exception {
        UUID attemptId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String sessionId = "cs_test_webhook_mysql_race";
        Instant createdAt = Instant.now().minusSeconds(10);
        PaymentAttempt attempt =
                PaymentAttempt.start(attemptId, orderId, 1, new BigDecimal("199000.00"), "VND", "STRIPE", createdAt);
        attempt.transition(
                PaymentStatus.REQUIRES_ACTION,
                sessionId,
                URI.create("https://checkout.stripe.com/c/pay/" + sessionId),
                null,
                createdAt.plusSeconds(1));
        paymentAttemptRepository.saveAndFlush(attempt);
        byte[] payload = StripeWebhookTestSupport.checkoutEvent(
                "evt_webhook_mysql_race",
                "checkout.session.completed",
                sessionId,
                attemptId,
                orderId,
                199000,
                "vnd",
                "complete",
                "paid",
                Instant.now());
        String signature = StripeWebhookTestSupport.signature(payload, Instant.now());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<StripeWebhookReceipt> receipts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<StripeWebhookReceipt> first = executor.submit(() -> receive(payload, signature, ready, start));
            Future<StripeWebhookReceipt> second = executor.submit(() -> receive(payload, signature, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            receipts = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        assertThat(receipts)
                .extracting(StripeWebhookReceipt::status)
                .containsExactlyInAnyOrder("PROCESSED", "DUPLICATE");
        PaymentAttempt reloaded = paymentAttemptRepository.findById(attemptId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(webhookEventRepository.count()).isEqualTo(1);
    }

    private StripeWebhookReceipt receive(byte[] payload, String signature, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent webhook start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent webhook start", exception);
        }
        return webhookService.receive(payload, signature);
    }
}
