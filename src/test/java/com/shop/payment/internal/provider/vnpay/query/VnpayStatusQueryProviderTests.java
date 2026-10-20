package com.shop.payment.internal.provider.vnpay.query;

import static com.shop.payment.internal.provider.vnpay.query.VnpayQueryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderStatus;
import com.shop.payment.provider.PaymentStatusQueryResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VnpayStatusQueryProviderTests {
    @Test
    void opensAfterFailuresThenRecoversThroughHalfOpenAndPublishesBoundedMetrics() {
        CircuitBreakerRegistry registry = new VnpayQueryConfiguration().vnpayQueryCircuitBreakerRegistry();
        CircuitBreaker breaker = registry.circuitBreaker("vnpayQuery");
        AtomicInteger calls = new AtomicInteger();
        var provider = new VnpayStatusQueryProvider(
                input -> {
                    if (calls.incrementAndGet() <= 5) {
                        throw new PaymentProviderException(PaymentProviderErrorType.TIMEOUT, "VNPAY_QUERY_TIMEOUT");
                    }
                    return result();
                },
                breaker);
        var meters = new SimpleMeterRegistry();
        try {
            new VnpayQueryConfiguration()
                    .vnpayQueryCircuitBreakerMetrics(registry)
                    .bindTo(meters);
            assertThat(provider.providerCode()).isEqualTo("VNPAY");
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> provider.query(request())).isInstanceOf(PaymentProviderException.class);
            }
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> provider.query(request()))
                    .isInstanceOfSatisfying(
                            PaymentProviderException.class,
                            error -> assertThat(error.getProviderErrorCode()).isEqualTo("VNPAY_QUERY_CIRCUIT_OPEN"));
            assertThat(calls).hasValue(5);
            breaker.transitionToHalfOpenState();
            assertThat(provider.query(request())).isEqualTo(result());
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
            assertThat(provider.query(request())).isEqualTo(result());
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(meters.get("resilience4j.circuitbreaker.calls")
                            .tag("name", "vnpayQuery")
                            .tag("kind", "failed")
                            .timer()
                            .count())
                    .isEqualTo(5);
            assertThat(meters.getMeters())
                    .allSatisfy(
                            meter -> assertThat(meter.getId().toString()).doesNotContain(ATTEMPT.toString(), "123456"));
        } finally {
            meters.close();
        }
        assertThat(breaker.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        assertThat(breaker.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(breaker.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50);
        assertThat(breaker.getCircuitBreakerConfig()
                        .getWaitIntervalFunctionInOpenState()
                        .apply(1))
                .isEqualTo(Duration.ofSeconds(30).toMillis());
    }

    @Test
    void ignoresNonRetryableErrorsAndDoesNotRetryBusinessRejections() {
        var registry = new VnpayQueryConfiguration().vnpayQueryCircuitBreakerRegistry();
        var breaker = registry.circuitBreaker("vnpayQuery");
        AtomicInteger calls = new AtomicInteger();
        var provider = new VnpayStatusQueryProvider(
                input -> {
                    calls.incrementAndGet();
                    throw new PaymentProviderException(
                            PaymentProviderErrorType.INVALID_REQUEST, "VNPAY_QUERY_HTTP_400");
                },
                breaker);
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> provider.query(request())).isInstanceOf(PaymentProviderException.class);
        }
        assertThat(calls).hasValue(6);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThatThrownBy(() -> provider.query(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void permitsOnlyTwoConcurrentCallsRejectsWithoutQueueAndReleasesPermits() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        var provider = new VnpayStatusQueryProvider(
                input -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("test release timed out");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    }
                    return result();
                },
                CircuitBreaker.ofDefaults("concurrentQuery"));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> provider.query(request()));
            var second = executor.submit(() -> provider.query(request()));
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> provider.query(request()))
                        .isInstanceOfSatisfying(
                                PaymentProviderException.class, error -> assertThat(error.getProviderErrorCode())
                                        .isEqualTo("VNPAY_QUERY_BUSY"));
                assertThat(calls).hasValue(2);
            } finally {
                release.countDown();
            }
            assertThat(first.get(3, TimeUnit.SECONDS)).isEqualTo(result());
            assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo(result());
            assertThat(provider.query(request())).isEqualTo(result());
            assertThat(calls).hasValue(3);
        }
    }

    private static PaymentStatusQueryResult result() {
        return new PaymentStatusQueryResult(PaymentProviderStatus.UNKNOWN, "ref", "123456", CLOCK.instant());
    }
}
