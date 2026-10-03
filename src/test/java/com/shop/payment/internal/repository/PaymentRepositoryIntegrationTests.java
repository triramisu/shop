package com.shop.payment.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentRepositoryIntegrationTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-03T00:00:00Z");

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsImmutableOrderTermsAndLifecycleState() {
        UUID orderId = UUID.randomUUID();
        PaymentAttempt attempt = paymentAttemptRepository.saveAndFlush(PaymentAttempt.start(
                UUID.randomUUID(), orderId, 1, new BigDecimal("250000.00"), "VND", "SANDBOX", CREATED_AT));
        attempt.transition(PaymentStatus.PENDING, "sandbox-reference-1", null, CREATED_AT.plusSeconds(1));
        paymentAttemptRepository.saveAndFlush(attempt);
        entityManager.clear();

        PaymentAttempt reloaded =
                paymentAttemptRepository.findById(attempt.getId()).orElseThrow();

        assertThat(reloaded.getOrderId()).isEqualTo(orderId);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("250000.00");
        assertThat(reloaded.getCurrency()).isEqualTo("VND");
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(reloaded.getProviderReference()).isEqualTo("sandbox-reference-1");
        assertThat(paymentAttemptRepository.findByOrderIdOrderByAttemptNumber(orderId))
                .extracting(PaymentAttempt::getAttemptNumber)
                .containsExactly(1);
    }

    @Test
    void enforcesAttemptAndProviderReferenceUniquenessInTheDatabase() {
        UUID orderId = UUID.randomUUID();
        PaymentAttempt first = PaymentAttempt.start(
                UUID.randomUUID(), orderId, 1, new BigDecimal("10.00"), "USD", "SANDBOX", CREATED_AT);
        first.transition(PaymentStatus.PENDING, "duplicate-reference", null, CREATED_AT.plusSeconds(1));
        paymentAttemptRepository.saveAndFlush(first);

        PaymentAttempt duplicateAttempt = PaymentAttempt.start(
                UUID.randomUUID(), orderId, 1, new BigDecimal("10.00"), "USD", "OTHER", CREATED_AT);
        assertThatThrownBy(() -> paymentAttemptRepository.saveAndFlush(duplicateAttempt))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(SQLException.class);
        entityManager.clear();

        PaymentAttempt duplicateReference = PaymentAttempt.start(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("10.00"), "USD", "SANDBOX", CREATED_AT);
        duplicateReference.transition(PaymentStatus.PENDING, "duplicate-reference", null, CREATED_AT.plusSeconds(1));
        assertThatThrownBy(() -> paymentAttemptRepository.saveAndFlush(duplicateReference))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void persistsHostedCheckoutUrlAndClearsItAfterCompletion() {
        PaymentAttempt attempt = PaymentAttempt.start(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("100.00"), "USD", "STRIPE", CREATED_AT);
        URI checkoutUrl = URI.create("https://checkout.stripe.com/c/pay/test-session");
        attempt.transition(
                PaymentStatus.REQUIRES_ACTION, "cs_test_session", checkoutUrl, null, CREATED_AT.plusSeconds(1));
        paymentAttemptRepository.saveAndFlush(attempt);
        entityManager.clear();

        PaymentAttempt awaitingAction =
                paymentAttemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(awaitingAction.getActionUrl()).isEqualTo(checkoutUrl);

        awaitingAction.transition(PaymentStatus.SUCCEEDED, null, null, null, CREATED_AT.plusSeconds(2));
        paymentAttemptRepository.saveAndFlush(awaitingAction);
        entityManager.clear();

        assertThat(paymentAttemptRepository
                        .findById(attempt.getId())
                        .orElseThrow()
                        .getActionUrl())
                .isNull();
    }
}
