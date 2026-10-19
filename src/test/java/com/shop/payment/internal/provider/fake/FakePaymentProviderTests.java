package com.shop.payment.internal.provider.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderRequest;
import com.shop.payment.provider.PaymentProviderResult;
import com.shop.payment.provider.PaymentProviderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FakePaymentProviderTests {

    private FakePaymentProviderProperties properties;
    private FakePaymentProviderControls controls;
    private FakePaymentProvider provider;

    @BeforeEach
    void setUp() {
        properties = new FakePaymentProviderProperties();
        properties.validate();
        controls = new FakePaymentProviderControls(properties);
        provider = new FakePaymentProvider(properties, controls);
    }

    @Test
    void returnsAndReplaysDeterministicSuccess() {
        PaymentProviderRequest request = request(UUID.randomUUID(), "fake-key-success");

        PaymentProviderResult first = provider.createPayment(request);
        PaymentProviderResult replay = provider.createPayment(request);

        assertThat(first).isEqualTo(replay);
        assertThat(first.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
        assertThat(first.providerReference()).startsWith("fake_");
        assertThat(provider.providerCode()).isEqualTo("FAKE");
    }

    @Test
    void rejectsDifferentPayloadReusingAnIdempotencyKey() {
        PaymentProviderRequest original = request(UUID.randomUUID(), "fake-key-conflict");
        PaymentProviderRequest conflicting = new PaymentProviderRequest(
                UUID.randomUUID(),
                original.orderId(),
                original.attemptNumber(),
                original.amount(),
                original.currency(),
                original.idempotencyKey());
        provider.createPayment(original);

        assertThatThrownBy(() -> provider.createPayment(conflicting))
                .isInstanceOfSatisfying(PaymentProviderException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(PaymentProviderErrorType.INVALID_REQUEST);
                    assertThat(exception.getProviderErrorCode()).isEqualTo("FAKE_IDEMPOTENCY_KEY_REUSED");
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    @Test
    void supportsDeclineAndTimeoutModesPerAttempt() {
        UUID declinedAttemptId = UUID.randomUUID();
        UUID timedOutAttemptId = UUID.randomUUID();
        controls.useMode(declinedAttemptId, FakePaymentMode.DECLINE);
        controls.useMode(timedOutAttemptId, FakePaymentMode.TIMEOUT);

        PaymentProviderResult declined = provider.createPayment(request(declinedAttemptId, "fake-key-decline"));

        assertThat(declined.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(declined.failureCode()).isEqualTo("FAKE_PAYMENT_DECLINED");
        assertThatThrownBy(() -> provider.createPayment(request(timedOutAttemptId, "fake-key-timeout")))
                .isInstanceOfSatisfying(PaymentProviderException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(PaymentProviderErrorType.TIMEOUT);
                    assertThat(exception.getProviderErrorCode()).isEqualTo("FAKE_PROVIDER_TIMEOUT");
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void simulatesDuplicateCallbacksWithOneProviderEventIdentity() {
        UUID attemptId = UUID.randomUUID();
        controls.useMode(attemptId, FakePaymentMode.PENDING);
        PaymentProviderRequest request = request(attemptId, "fake-key-pending");
        FakePaymentCallbackSimulator simulator = new FakePaymentCallbackSimulator(provider, properties);

        List<FakePaymentCallback> callbacks = simulator.simulate(request, 3);

        assertThat(callbacks).hasSize(3).allMatch(callback -> callback.equals(callbacks.getFirst()));
        assertThat(callbacks.getFirst().eventId()).startsWith("fake_event_");
        assertThat(callbacks.getFirst().status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
        assertThatThrownBy(() -> simulator.simulate(request, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delivery count");
    }

    private PaymentProviderRequest request(UUID paymentAttemptId, String idempotencyKey) {
        return new PaymentProviderRequest(
                paymentAttemptId, UUID.randomUUID(), 1, new BigDecimal("199000.00"), "VND", idempotencyKey);
    }
}
