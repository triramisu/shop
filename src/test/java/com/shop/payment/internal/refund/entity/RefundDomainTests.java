package com.shop.payment.internal.refund.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.provider.RefundProviderResult;
import com.shop.payment.provider.RefundProviderStatus;
import com.shop.shared.error.AppException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RefundDomainTests {
    private final Instant now = Instant.parse("2026-10-16T00:00:00Z");

    @Test
    void tracksAllocatedAndSucceededSeparatelyAndOnlyFailureReleasesHold() {
        var budget = budget();
        budget.reserve(money("40"), now);
        budget.reserve(money("60"), now);
        assertThat(budget.remainingAmount()).isZero();
        budget.settle(money("40"), true, now);
        assertThat(budget.getSucceededAmount()).isEqualByComparingTo("40");
        assertThat(budget.getAllocatedAmount()).isEqualByComparingTo("100");
        budget.settle(money("60"), false, now);
        assertThat(budget.remainingAmount()).isEqualByComparingTo("60");
        assertThatThrownBy(() -> budget.settle(money("1"), true, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> budget.reserve(money("61"), now)).isInstanceOf(AppException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "100000000000000000"})
    void rejectsInvalidMoneyWithoutMutation(String amount) {
        var budget = budget();
        assertThatThrownBy(() -> budget.reserve(new BigDecimal(amount), now)).isInstanceOf(AppException.class);
        assertThat(budget.remainingAmount()).isEqualByComparingTo("100");
    }

    @Test
    void rejectsBackwardsTimeAndInvalidBudget() {
        var budget = budget();
        assertThatThrownBy(() -> budget.reserve(money("1"), now.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefundBudget.open(UUID.randomUUID(), money("0"), "USD", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefundBudget.open(UUID.randomUUID(), money("1"), "usd", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeoutThenPendingThenSuccessIsMonotonicAndTerminalCannotDowngrade() {
        var refund = attempt();
        assertThat(refund.apply(result(RefundProviderStatus.UNKNOWN, null), now))
                .isTrue();
        assertThat(refund.apply(result(RefundProviderStatus.PENDING, "re_test"), now.plusSeconds(1)))
                .isTrue();
        assertThatThrownBy(() -> refund.apply(result(RefundProviderStatus.SUCCEEDED, "re_other"), now.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> refund.apply(result(RefundProviderStatus.SUCCEEDED, "re_test"), now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(refund.apply(result(RefundProviderStatus.SUCCEEDED, "re_test"), now.plusSeconds(2)))
                .isTrue();
        assertThat(refund.apply(
                        new RefundProviderResult(RefundProviderStatus.FAILED, "re_test", "DECLINED"),
                        now.plusSeconds(3)))
                .isFalse();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    }

    @Test
    void requiresCapturedPaymentAndSafeProviderResult() {
        var payment = PaymentAttempt.start(UUID.randomUUID(), UUID.randomUUID(), 1, money("100"), "USD", "FAKE", now);
        assertThatThrownBy(
                        () -> RefundAttempt.create(payment, money("1"), "a".repeat(64), "b".repeat(64), "admin", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result(RefundProviderStatus.SUCCEEDED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result(RefundProviderStatus.PENDING, "bad reference"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefundProviderResult(RefundProviderStatus.FAILED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefundProviderResult(RefundProviderStatus.UNKNOWN, null, "ERROR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RefundBudget budget() {
        return RefundBudget.open(UUID.randomUUID(), money("100"), "USD", now);
    }

    private RefundAttempt attempt() {
        var payment = PaymentAttempt.start(UUID.randomUUID(), UUID.randomUUID(), 1, money("100"), "USD", "FAKE", now);
        payment.transition(PaymentStatus.SUCCEEDED, "fake_payment", null, now);
        return RefundAttempt.create(payment, money("10"), "a".repeat(64), "b".repeat(64), "admin", now);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private RefundProviderResult result(RefundProviderStatus status, String reference) {
        return new RefundProviderResult(status, reference, null);
    }
}
