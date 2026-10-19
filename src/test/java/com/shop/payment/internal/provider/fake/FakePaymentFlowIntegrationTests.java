package com.shop.payment.internal.provider.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.processing.PaymentAttemptSnapshot;
import com.shop.payment.processing.PaymentInitiationCommand;
import com.shop.payment.processing.PaymentInitiationOperations;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FakePaymentFlowIntegrationTests {

    @Autowired
    private PaymentInitiationOperations paymentInitiationOperations;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private FakePaymentProviderControls controls;

    @BeforeEach
    void clearControls() {
        controls.clear();
    }

    @Test
    void persistsSuccessfulPaymentAndReturnsTheSameTerminalAttemptOnRetry() {
        PaymentInitiationCommand command = command(UUID.randomUUID(), UUID.randomUUID(), 1);

        PaymentAttemptSnapshot first = paymentInitiationOperations.initiate(command);
        PaymentAttemptSnapshot replay = paymentInitiationOperations.initiate(command);

        assertThat(first.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(first.providerCode()).isEqualTo("FAKE");
        assertThat(first.providerReference()).startsWith("fake_");
        assertThat(first.completedAt()).isNotNull();
        assertThat(replay).isEqualTo(first);
        assertSinglePersistedAttempt(command.orderId(), first.id(), PaymentStatus.SUCCEEDED);
    }

    @Test
    void persistsDeclinedPaymentAsFailed() {
        UUID paymentAttemptId = UUID.randomUUID();
        controls.useMode(paymentAttemptId, FakePaymentMode.DECLINE);
        PaymentInitiationCommand command = command(paymentAttemptId, UUID.randomUUID(), 1);

        PaymentAttemptSnapshot result = paymentInitiationOperations.initiate(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("FAKE_PAYMENT_DECLINED");
        assertThat(result.completedAt()).isNotNull();
        assertSinglePersistedAttempt(command.orderId(), result.id(), PaymentStatus.FAILED);
    }

    @Test
    void persistsTimeoutAsUnknownAndSafelyRetriesTheProviderOutcome() {
        UUID paymentAttemptId = UUID.randomUUID();
        controls.useMode(paymentAttemptId, FakePaymentMode.TIMEOUT);
        PaymentInitiationCommand command = command(paymentAttemptId, UUID.randomUUID(), 1);

        PaymentAttemptSnapshot first = paymentInitiationOperations.initiate(command);
        PaymentAttemptSnapshot replay = paymentInitiationOperations.initiate(command);

        assertThat(first.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(first.completedAt()).isNull();
        assertThat(replay).isEqualTo(first);
        assertSinglePersistedAttempt(command.orderId(), first.id(), PaymentStatus.UNKNOWN);
    }

    @Test
    void leavesPendingPaymentOpenWithoutCallingTheProviderAgain() {
        UUID paymentAttemptId = UUID.randomUUID();
        controls.useMode(paymentAttemptId, FakePaymentMode.PENDING);
        PaymentInitiationCommand command = command(paymentAttemptId, UUID.randomUUID(), 1);

        PaymentAttemptSnapshot first = paymentInitiationOperations.initiate(command);
        PaymentAttemptSnapshot replay = paymentInitiationOperations.initiate(command);

        assertThat(first.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(first.completedAt()).isNull();
        assertThat(replay).isEqualTo(first);
        assertSinglePersistedAttempt(command.orderId(), first.id(), PaymentStatus.PENDING);
    }

    @Test
    void rejectsConflictingImmutableTermsForTheSameOrderAttempt() {
        UUID orderId = UUID.randomUUID();
        PaymentInitiationCommand original = command(UUID.randomUUID(), orderId, 1);
        paymentInitiationOperations.initiate(original);
        PaymentInitiationCommand conflicting =
                new PaymentInitiationCommand(UUID.randomUUID(), orderId, 1, new BigDecimal("200000.00"), "VND");

        assertThatThrownBy(() -> paymentInitiationOperations.initiate(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable terms conflict");
        assertThat(paymentAttemptRepository.findByOrderIdOrderByAttemptNumber(orderId))
                .hasSize(1);
    }

    private PaymentInitiationCommand command(UUID paymentAttemptId, UUID orderId, int attemptNumber) {
        return new PaymentInitiationCommand(
                paymentAttemptId, orderId, attemptNumber, new BigDecimal("199000.00"), "VND");
    }

    private void assertSinglePersistedAttempt(UUID orderId, UUID attemptId, PaymentStatus expectedStatus) {
        assertThat(paymentAttemptRepository.findByOrderIdOrderByAttemptNumber(orderId))
                .singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.getId()).isEqualTo(attemptId);
                    assertThat(attempt.getStatus()).isEqualTo(expectedStatus);
                });
    }
}
