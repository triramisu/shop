package com.shop.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentProviderContractTests {

    @Test
    void normalizesProviderRequestWithoutAcceptingCardData() {
        PaymentProviderRequest request = new PaymentProviderRequest(
                UUID.randomUUID(), UUID.randomUUID(), 2, new BigDecimal("49.90"), "vnd", "payment-attempt-key-0002");

        assertThat(request.amount()).isEqualByComparingTo("49.90");
        assertThat(request.currency()).isEqualTo("VND");
        assertThat(request.attemptNumber()).isEqualTo(2);
        assertThat(PaymentProviderRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("cardNumber", "pan", "cvv", "expiry");
    }

    @Test
    void protectsProviderResultInvariants() {
        PaymentProviderResult requiresAction = new PaymentProviderResult(
                PaymentProviderStatus.REQUIRES_ACTION,
                "provider-reference-1",
                URI.create("https://payments.example.test/session/1#provider-state"),
                null);
        PaymentProviderResult unknown = new PaymentProviderResult(PaymentProviderStatus.UNKNOWN, null, null, null);
        PaymentProviderResult declined =
                new PaymentProviderResult(PaymentProviderStatus.FAILED, "reference", null, "declined");

        assertThat(requiresAction.actionUrl()).hasScheme("https");
        assertThat(requiresAction.actionUrl().getFragment()).isEqualTo("provider-state");
        assertThat(unknown.providerReference()).isNull();
        assertThat(declined.failureCode()).isEqualTo("DECLINED");
        assertThatThrownBy(
                        () -> new PaymentProviderResult(PaymentProviderStatus.REQUIRES_ACTION, "reference", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentProviderResult(
                        PaymentProviderStatus.SUCCEEDED,
                        "reference",
                        URI.create("https://payments.example.test/unexpected"),
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentProviderResult(PaymentProviderStatus.FAILED, "reference", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentProviderResult(
                        PaymentProviderStatus.REQUIRES_ACTION,
                        "reference",
                        URI.create("http://payments.example.test/session/1"),
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new PaymentProviderResult(
                        PaymentProviderStatus.REQUIRES_ACTION,
                        "reference",
                        URI.create("https://user@payments.example.test/session/1"),
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user information");
    }

    @Test
    void classifiesRetryableAndPermanentProviderFailures() {
        PaymentProviderException timeout =
                new PaymentProviderException(PaymentProviderErrorType.TIMEOUT, "PROVIDER_TIMEOUT");
        PaymentProviderException invalidRequest =
                new PaymentProviderException(PaymentProviderErrorType.INVALID_REQUEST, "INVALID_AMOUNT");

        assertThat(timeout.isRetryable()).isTrue();
        assertThat(invalidRequest.isRetryable()).isFalse();
        assertThat(timeout.getMessage()).doesNotContain("token", "credential", "response body");
        assertThatThrownBy(() -> new PaymentProviderException(
                        PaymentProviderErrorType.PROTOCOL_ERROR, "raw provider response contains spaces"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
