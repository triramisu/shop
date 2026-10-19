package com.shop.payment.internal.provider.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.RefundProviderRequest;
import com.shop.payment.provider.RefundProviderStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeRefundProviderTests {
    @Test
    void providerReplaysIdenticalRequestAndRejectsConflictingMoney() {
        var provider = new FakeRefundProvider();
        var request = request();
        var first = provider.refund(request);
        provider.useMode(request.paymentAttemptId(), FakeRefundProvider.Mode.DECLINE);
        assertThat(provider.refund(request)).isEqualTo(first);
        assertThat(provider.invocationCount(request.paymentAttemptId())).isEqualTo(2);
        assertThatThrownBy(() -> provider.refund(new RefundProviderRequest(
                        request.refundId(),
                        request.paymentAttemptId(),
                        request.orderId(),
                        request.providerPaymentReference(),
                        new BigDecimal("11.00"),
                        "USD",
                        request.idempotencyKey())))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void deterministicTimeoutCannotTurnIntoSuccessOnRetry() {
        var provider = new FakeRefundProvider();
        var request = request();
        provider.useMode(request.paymentAttemptId(), FakeRefundProvider.Mode.TIMEOUT);
        assertThatThrownBy(() -> provider.refund(request)).isInstanceOf(PaymentProviderException.class);
        provider.useMode(request.paymentAttemptId(), FakeRefundProvider.Mode.SUCCESS);
        assertThatThrownBy(() -> provider.refund(request)).isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void pendingAndDeclineAreNotSuccess() {
        var provider = new FakeRefundProvider();
        for (var mode :
                new FakeRefundProvider.Mode[] {FakeRefundProvider.Mode.PENDING, FakeRefundProvider.Mode.DECLINE}) {
            var request = request();
            provider.useMode(request.paymentAttemptId(), mode);
            assertThat(provider.refund(request).status())
                    .isEqualTo(
                            mode == FakeRefundProvider.Mode.PENDING
                                    ? RefundProviderStatus.PENDING
                                    : RefundProviderStatus.FAILED);
        }
    }

    @Test
    void providerRequestRejectsUnsafeBoundaries() {
        var valid = request();
        for (var money : new String[] {"0", "-1", "0.001", "100000000000000000.00"}) {
            assertThatThrownBy(() -> new RefundProviderRequest(
                            valid.refundId(),
                            valid.paymentAttemptId(),
                            valid.orderId(),
                            "payment_ref",
                            new BigDecimal(money),
                            "USD",
                            "refund-key"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new RefundProviderRequest(
                        valid.refundId(),
                        valid.paymentAttemptId(),
                        valid.orderId(),
                        " ",
                        valid.amount(),
                        "USD",
                        "refund-key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefundProviderRequest(
                        valid.refundId(),
                        valid.paymentAttemptId(),
                        valid.orderId(),
                        "payment_ref",
                        valid.amount(),
                        "usd",
                        "refund-key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefundProviderRequest(
                        valid.refundId(),
                        valid.paymentAttemptId(),
                        valid.orderId(),
                        "payment_ref",
                        valid.amount(),
                        "USD",
                        "key with space"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RefundProviderRequest request() {
        var refundId = UUID.randomUUID();
        return new RefundProviderRequest(
                refundId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "payment_ref",
                new BigDecimal("10.00"),
                "USD",
                "refund-" + refundId);
    }
}
