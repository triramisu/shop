package com.shop.payment.internal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.event.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAttemptTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void keepsAuthoritativeOrderTermsAndEmitsStatusChanges() {
        UUID attemptId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentAttempt attempt = PaymentAttempt.start(
                attemptId, orderId, 1, new BigDecimal("125.50"), "usd", "fake-provider", CREATED_AT);

        var pendingEvent =
                attempt.transition(PaymentStatus.PENDING, "provider-reference-1", null, CREATED_AT.plusSeconds(1));
        var successEvent = attempt.transition(PaymentStatus.SUCCEEDED, null, null, CREATED_AT.plusSeconds(2));

        assertThat(attempt.getId()).isEqualTo(attemptId);
        assertThat(attempt.getOrderId()).isEqualTo(orderId);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getAmount()).isEqualByComparingTo("125.50");
        assertThat(attempt.getCurrency()).isEqualTo("USD");
        assertThat(attempt.getProviderCode()).isEqualTo("FAKE-PROVIDER");
        assertThat(attempt.getProviderReference()).isEqualTo("provider-reference-1");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(attempt.getCompletedAt()).isEqualTo(CREATED_AT.plusSeconds(2));
        assertThat(pendingEvent.previousStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(pendingEvent.currentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(successEvent.previousStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(successEvent.currentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(successEvent.amount()).isEqualByComparingTo("125.50");
    }

    @Test
    void rejectsInvalidAttemptTerms() {
        UUID attemptId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> PaymentAttempt.start(
                        attemptId, orderId, 0, new BigDecimal("10.00"), "USD", "PROVIDER", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        PaymentAttempt.start(attemptId, orderId, 1, BigDecimal.ZERO, "USD", "PROVIDER", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentAttempt.start(
                        attemptId, orderId, 1, new BigDecimal("10.001"), "USD", "PROVIDER", CREATED_AT))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> PaymentAttempt.start(
                        attemptId, orderId, 1, new BigDecimal("10.00"), "US", "PROVIDER", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        PaymentAttempt.start(attemptId, orderId, 1, new BigDecimal("10.00"), "USD", "?", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blocksBackwardTerminalAndInconsistentFailureTransitions() {
        PaymentAttempt attempt = newAttempt();

        assertThatThrownBy(() -> attempt.transition(PaymentStatus.PENDING, null, null, CREATED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider reference");
        assertThatThrownBy(() -> attempt.transition(
                        PaymentStatus.FAILED, "provider-reference-2", null, CREATED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failure code");

        attempt.transition(PaymentStatus.UNKNOWN, null, null, CREATED_AT.plusSeconds(2));
        attempt.transition(PaymentStatus.PENDING, "provider-reference-2", null, CREATED_AT.plusSeconds(3));

        assertThatThrownBy(() -> attempt.transition(PaymentStatus.CREATED, null, null, CREATED_AT.plusSeconds(4)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> attempt.transition(PaymentStatus.SUCCEEDED, null, null, CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backwards");

        attempt.transition(PaymentStatus.FAILED, null, "DECLINED", CREATED_AT.plusSeconds(4));
        assertThat(attempt.getFailureCode()).isEqualTo("DECLINED");
        assertThatThrownBy(() -> attempt.transition(PaymentStatus.PENDING, null, null, CREATED_AT.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsProviderReferenceImmutableAfterAssignment() {
        PaymentAttempt attempt = newAttempt();
        attempt.transition(PaymentStatus.PENDING, "provider-reference-1", null, CREATED_AT.plusSeconds(1));

        assertThatThrownBy(() -> attempt.transition(
                        PaymentStatus.SUCCEEDED, "provider-reference-2", null, CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be changed");
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(attempt.getProviderReference()).isEqualTo("provider-reference-1");
    }

    @Test
    void recordsAPermanentLocalFailureBeforeAProviderReferenceExists() {
        PaymentAttempt attempt = newAttempt();

        var event = attempt.transition(PaymentStatus.FAILED, null, "CONFIGURATION_ERROR", CREATED_AT.plusSeconds(1));

        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(attempt.getProviderReference()).isNull();
        assertThat(attempt.getFailureCode()).isEqualTo("CONFIGURATION_ERROR");
        assertThat(event.providerReference()).isNull();
    }

    private PaymentAttempt newAttempt() {
        return PaymentAttempt.start(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("10.00"), "USD", "PROVIDER", CREATED_AT);
    }
}
