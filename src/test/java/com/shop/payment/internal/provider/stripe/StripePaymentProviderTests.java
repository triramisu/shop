package com.shop.payment.internal.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderRequest;
import com.shop.payment.provider.PaymentProviderResult;
import com.shop.payment.provider.PaymentProviderStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StripePaymentProviderTests {

    @Test
    void returnsAllowlistedHostedCheckoutSession() {
        PaymentProviderRequest request = request();
        AtomicInteger calls = new AtomicInteger();
        StripeCheckoutGateway gateway = stripeRequest -> {
            calls.incrementAndGet();
            return session(stripeRequest, URI.create("https://checkout.stripe.com/c/pay/session"));
        };

        PaymentProviderResult result = provider(gateway, 2, 20, 10).createPayment(request);

        assertThat(result.status()).isEqualTo(PaymentProviderStatus.REQUIRES_ACTION);
        assertThat(result.providerReference()).isEqualTo("cs_test_session");
        assertThat(result.actionUrl()).isEqualTo(URI.create("https://checkout.stripe.com/c/pay/session"));
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesIdempotentSessionCreationAfterTimeout() {
        PaymentProviderRequest request = request();
        AtomicInteger calls = new AtomicInteger();
        StripeCheckoutGateway gateway = stripeRequest -> {
            if (calls.incrementAndGet() == 1) {
                throw new PaymentProviderException(PaymentProviderErrorType.TIMEOUT, "STRIPE_TIMEOUT");
            }
            return session(stripeRequest, URI.create("https://checkout.stripe.com/c/pay/retry"));
        };

        PaymentProviderResult result = provider(gateway, 2, 20, 10).createPayment(request);

        assertThat(result.status()).isEqualTo(PaymentProviderStatus.REQUIRES_ACTION);
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRetryPermanentAuthenticationFailure() {
        AtomicInteger calls = new AtomicInteger();
        StripeCheckoutGateway gateway = stripeRequest -> {
            calls.incrementAndGet();
            throw new PaymentProviderException(
                    PaymentProviderErrorType.AUTHENTICATION_FAILED, "STRIPE_AUTHENTICATION_FAILED");
        };

        assertThatThrownBy(() -> provider(gateway, 3, 20, 10).createPayment(request()))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class, exception -> assertThat(exception.getErrorType())
                                .isEqualTo(PaymentProviderErrorType.AUTHENTICATION_FAILED));
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsAndRetriesMismatchedAmountWithoutReturningCheckoutUrl() {
        AtomicInteger calls = new AtomicInteger();
        StripeCheckoutGateway gateway = stripeRequest -> {
            calls.incrementAndGet();
            StripeCheckoutSession valid =
                    session(stripeRequest, URI.create("https://checkout.stripe.com/c/pay/mismatch"));
            return new StripeCheckoutSession(
                    valid.id(),
                    valid.checkoutUrl(),
                    valid.status(),
                    valid.paymentStatus(),
                    valid.clientReferenceId(),
                    valid.amountTotal() + 1,
                    valid.currency());
        };

        assertThatThrownBy(() -> provider(gateway, 2, 20, 10).createPayment(request()))
                .isInstanceOfSatisfying(PaymentProviderException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(PaymentProviderErrorType.PROTOCOL_ERROR);
                    assertThat(exception.getProviderErrorCode()).isEqualTo("STRIPE_SESSION_MISMATCH");
                });
        assertThat(calls).hasValue(2);
    }

    @Test
    void rejectsCheckoutUrlOutsideAllowlist() {
        AtomicInteger calls = new AtomicInteger();
        StripeCheckoutGateway gateway = stripeRequest -> {
            calls.incrementAndGet();
            return session(stripeRequest, URI.create("https://evil.example/checkout"));
        };

        assertThatThrownBy(() -> provider(gateway, 2, 20, 10).createPayment(request()))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class, exception -> assertThat(exception.getProviderErrorCode())
                                .isEqualTo("STRIPE_CHECKOUT_URL_REJECTED"));
        assertThat(calls).hasValue(2);
    }

    @Test
    void opensCircuitAfterConfiguredRetryableFailures() {
        AtomicInteger calls = new AtomicInteger();
        StripeCheckoutGateway gateway = stripeRequest -> {
            calls.incrementAndGet();
            throw new PaymentProviderException(
                    PaymentProviderErrorType.TEMPORARY_UNAVAILABLE, "STRIPE_SERVER_UNAVAILABLE");
        };
        StripePaymentProvider provider = provider(gateway, 1, 2, 2);

        assertThatThrownBy(() -> provider.createPayment(request())).isInstanceOf(PaymentProviderException.class);
        assertThatThrownBy(() -> provider.createPayment(request())).isInstanceOf(PaymentProviderException.class);
        assertThatThrownBy(() -> provider.createPayment(request()))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class, exception -> assertThat(exception.getProviderErrorCode())
                                .isEqualTo("STRIPE_CIRCUIT_OPEN"));
        assertThat(calls).hasValue(2);
    }

    @Test
    void mapsPaidAndExpiredSessionsToDefinitiveResults() {
        PaymentProviderResult paid = provider(
                        stripeRequest -> session(stripeRequest, null, "complete", "paid"), 1, 20, 10)
                .createPayment(request());
        PaymentProviderResult expired = provider(
                        stripeRequest -> session(stripeRequest, null, "expired", "unpaid"), 1, 20, 10)
                .createPayment(request());

        assertThat(paid.status()).isEqualTo(PaymentProviderStatus.SUCCEEDED);
        assertThat(paid.actionUrl()).isNull();
        assertThat(expired.status()).isEqualTo(PaymentProviderStatus.FAILED);
        assertThat(expired.failureCode()).isEqualTo("STRIPE_SESSION_EXPIRED");
    }

    @Test
    void rejectsUnexpectedProviderStatus() {
        StripeCheckoutGateway gateway = stripeRequest -> session(stripeRequest, null, "complete", "unpaid");

        assertThatThrownBy(() -> provider(gateway, 1, 20, 10).createPayment(request()))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class, exception -> assertThat(exception.getProviderErrorCode())
                                .isEqualTo("STRIPE_STATUS_UNEXPECTED"));
    }

    @Test
    void rejectsSessionReferenceOrderAndCurrencyMismatches() {
        PaymentProviderRequest request = request();
        StripeCheckoutRequest expected = requestFactory().create(request);
        StripeCheckoutSession valid = session(expected, URI.create("https://checkout.stripe.com/c/pay/session"));
        StripeCheckoutSession invalidReference = new StripeCheckoutSession(
                "invalid-session",
                valid.checkoutUrl(),
                valid.status(),
                valid.paymentStatus(),
                valid.clientReferenceId(),
                valid.amountTotal(),
                valid.currency());
        StripeCheckoutSession invalidOrder = new StripeCheckoutSession(
                valid.id(),
                valid.checkoutUrl(),
                valid.status(),
                valid.paymentStatus(),
                UUID.randomUUID().toString(),
                valid.amountTotal(),
                valid.currency());
        StripeCheckoutSession invalidCurrency = new StripeCheckoutSession(
                valid.id(),
                valid.checkoutUrl(),
                valid.status(),
                valid.paymentStatus(),
                valid.clientReferenceId(),
                valid.amountTotal(),
                "eur");

        assertSessionMismatch(request, invalidReference);
        assertSessionMismatch(request, invalidOrder);
        assertSessionMismatch(request, invalidCurrency);
    }

    @Test
    void rejectsCheckoutUrlsWithUnsafeComponents() {
        PaymentProviderRequest request = request();
        for (URI unsafeUrl : Set.of(
                URI.create("http://checkout.stripe.com/c/pay/session"),
                URI.create("https://user@checkout.stripe.com/c/pay/session"),
                URI.create("https://checkout.stripe.com:8443/c/pay/session"))) {
            StripeCheckoutGateway gateway = stripeRequest -> session(stripeRequest, unsafeUrl);

            assertThatThrownBy(() -> provider(gateway, 1, 20, 10).createPayment(request))
                    .isInstanceOfSatisfying(
                            PaymentProviderException.class, exception -> assertThat(exception.getProviderErrorCode())
                                    .isEqualTo("STRIPE_CHECKOUT_URL_REJECTED"));
        }
    }

    @Test
    void acceptsAllowlistedStripeCheckoutUrlWithProviderFragment() {
        PaymentProviderRequest request = request();
        URI checkoutUrl = URI.create("https://checkout.stripe.com/c/pay/session#provider-state");
        StripeCheckoutGateway gateway = stripeRequest -> session(stripeRequest, checkoutUrl);

        PaymentProviderResult result = provider(gateway, 1, 20, 10).createPayment(request);

        assertThat(result.status()).isEqualTo(PaymentProviderStatus.REQUIRES_ACTION);
        assertThat(result.actionUrl()).isEqualTo(checkoutUrl);
    }

    private StripePaymentProvider provider(
            StripeCheckoutGateway gateway, int maxAttempts, int slidingWindowSize, int minimumCalls) {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "stripe-test",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(slidingWindowSize)
                        .minimumNumberOfCalls(minimumCalls)
                        .failureRateThreshold(50.0F)
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .recordException(exception -> exception instanceof PaymentProviderException providerException
                                && providerException.isRetryable())
                        .build());
        Retry retry = Retry.of(
                "stripe-test",
                RetryConfig.custom()
                        .maxAttempts(maxAttempts)
                        .waitDuration(Duration.ofMillis(1))
                        .retryOnException(exception -> exception instanceof PaymentProviderException providerException
                                && providerException.isRetryable())
                        .build());
        return new StripePaymentProvider(
                gateway, requestFactory(), Set.of("checkout.stripe.com"), circuitBreaker, retry);
    }

    private StripeCheckoutRequestFactory requestFactory() {
        return new StripeCheckoutRequestFactory(
                URI.create("https://shop.example.com/api/payments/checkout/return"),
                URI.create("https://shop.example.com/api/payments/checkout/cancel"),
                new StripeCheckoutStateSigner(
                        StripeTestCredentials.stateSigningKey(),
                        Duration.ofHours(1),
                        Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC)));
    }

    private PaymentProviderRequest request() {
        return new PaymentProviderRequest(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("10.25"), "USD", "stripe-idempotency-key");
    }

    private StripeCheckoutSession session(StripeCheckoutRequest request, URI checkoutUrl) {
        return session(request, checkoutUrl, "open", "unpaid");
    }

    private StripeCheckoutSession session(
            StripeCheckoutRequest request, URI checkoutUrl, String status, String paymentStatus) {
        return new StripeCheckoutSession(
                "cs_test_session",
                checkoutUrl,
                status,
                paymentStatus,
                request.orderId().toString(),
                request.amountInMinorUnit(),
                request.currency());
    }

    private void assertSessionMismatch(PaymentProviderRequest request, StripeCheckoutSession session) {
        StripeCheckoutGateway gateway = stripeRequest -> session;
        assertThatThrownBy(() -> provider(gateway, 1, 20, 10).createPayment(request))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class, exception -> assertThat(exception.getProviderErrorCode())
                                .isEqualTo("STRIPE_SESSION_MISMATCH"));
    }
}
