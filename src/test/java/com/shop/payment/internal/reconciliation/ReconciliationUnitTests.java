package com.shop.payment.internal.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.service.PaymentLifecycleEventPublisher;
import com.shop.payment.provider.PaymentProviderStatus;
import com.shop.payment.provider.PaymentStatusQueryOperations;
import com.shop.payment.provider.PaymentStatusQueryRequest;
import com.shop.payment.provider.PaymentStatusQueryResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class ReconciliationUnitTests {
    @ParameterizedTest
    @ValueSource(strings = {"READY", "LEASED", "DONE", "MANUAL"})
    void rechecksSelectedCheckpointAfterWaitingForPaymentLock(String state) {
        Instant now = Instant.parse("2026-10-20T00:00:00Z");
        UUID id = UUID.randomUUID();
        var payment =
                PaymentAttempt.start(id, UUID.randomUUID(), 1, BigDecimal.ONE, "VND", "VNPAY", now.minusSeconds(1200));
        payment.transition(
                PaymentStatus.REQUIRES_ACTION,
                id.toString().replace("-", ""),
                URI.create("https://api.example.com/start"),
                null,
                now.minusSeconds(1200));
        var repository = mock(ReconciliationRepository.class);
        var payments = mock(PaymentAttemptRepository.class);
        when(repository.lockProvider()).thenReturn(now);
        when(repository.candidate(any(), any())).thenReturn(Optional.of(id));
        when(payments.findForProviderUpdate(id)).thenReturn(Optional.of(payment));
        when(repository.lock(id))
                .thenReturn(new ReconciliationRepository.Row(
                        id,
                        state,
                        1,
                        now.plusSeconds(60),
                        now.plusSeconds(3600),
                        UUID.randomUUID(),
                        now,
                        now.plusSeconds(30),
                        0L));
        var store = new ReconciliationStore(
                repository, payments, mock(PaymentLifecycleEventPublisher.class), new ReconciliationProperties());
        assertThat(store.claim(now)).isEmpty();
        verify(repository, never()).claim(any(), any());
    }

    @Test
    void queryReceiptMetadataAndCallerNonceCannotBeMissingOrMalformed() {
        assertThatThrownBy(() -> new PaymentStatusQueryRequest(
                        UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "VND", Instant.EPOCH, null))
                .isInstanceOf(NullPointerException.class);
        for (String response : List.of("", "bad|response", "a".repeat(33))) {
            assertThatThrownBy(() -> new PaymentStatusQueryResult(
                            PaymentProviderStatus.SUCCEEDED, "ref", "1", Instant.EPOCH, response, "a".repeat(64)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String hash : List.of("", "A".repeat(64), "x".repeat(64))) {
            assertThatThrownBy(() -> new PaymentStatusQueryResult(
                            PaymentProviderStatus.SUCCEEDED, "ref", "1", Instant.EPOCH, "response1", hash))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new PaymentStatusQueryResult(
                        PaymentProviderStatus.SUCCEEDED, "ref", "1", Instant.EPOCH, null, "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentStatusQueryResult(
                        PaymentProviderStatus.SUCCEEDED, "ref", "1", Instant.EPOCH, "response1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsUnboundedSettings(Consumer<ReconciliationProperties> mutate) {
        var p = new ReconciliationProperties();
        mutate.accept(p);
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }

    static Stream<Consumer<ReconciliationProperties>> invalidSettings() {
        return Stream.of(
                p -> p.setBatchSize(0),
                p -> p.setBatchSize(21),
                p -> p.setMaxAttempts(0),
                p -> p.setMaxAttempts(21),
                p -> p.setMinimumAge(null),
                p -> p.setMinimumAge(Duration.ZERO),
                p -> p.setMinimumAge(Duration.ofHours(2)),
                p -> p.setMaximumAge(Duration.ofMinutes(59)),
                p -> p.setMaximumAge(Duration.ofDays(8)),
                p -> p.setLeaseDuration(Duration.ofSeconds(19)),
                p -> p.setLeaseDuration(Duration.ofMinutes(3)),
                p -> p.setProviderInterval(Duration.ofSeconds(4)),
                p -> p.setProviderInterval(Duration.ofMinutes(2)),
                p -> p.setRetryBase(Duration.ofSeconds(29)),
                p -> p.setRetryBase(Duration.ofMinutes(11)),
                p -> p.setRetryMax(Duration.ofDays(1)),
                p -> p.setRetryMax(Duration.ofSeconds(10)),
                p -> p.setFixedDelay(Duration.ZERO),
                p -> p.setFixedDelay(Duration.ofMinutes(6)),
                p -> p.setInitialDelay(Duration.ZERO),
                p -> p.setInitialDelay(Duration.ofMinutes(6)));
    }

    @Test
    void backoffIsBoundedDeterministicAndDefaultsRemainOff() {
        var p = new ReconciliationProperties();
        p.validate();
        assertThat(p.isEnabled()).isFalse();
        UUID id = UUID.randomUUID();
        for (int count = 1; count <= 20; count++) {
            var delay = p.backoff(id, count);
            assertThat(delay).isBetween(p.getRetryBase(), p.getRetryMax());
            assertThat(delay).isEqualTo(p.backoff(id, count));
        }
        assertThat(p.backoff(id, 20)).isEqualTo(p.getRetryMax());
    }

    @Test
    void disabledConfigurationNeedsNoDatabaseOrProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(ReconciliationConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(ReconciliationStore.class));
    }

    @Test
    void enabledConfigurationRequiresSandboxQueryAndBuildsJobWithoutStartingNetwork() {
        runner("VNPAY")
                .withPropertyValues("app.payment.provider.type=vnpay", "app.payment.vnpay.query.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ReconciliationJob.class));
        runner("VNPAY")
                .withPropertyValues("app.payment.provider.type=fake", "app.payment.vnpay.query.enabled=true")
                .run(context -> assertThat(context).hasFailed());
        runner("VNPAY").withPropertyValues("app.payment.provider.type=vnpay").run(context -> assertThat(context)
                .hasFailed());
        runner("STRIPE")
                .withPropertyValues("app.payment.provider.type=vnpay", "app.payment.vnpay.query.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void poisonItemDoesNotPreventNextClaimAndOnlyFiniteMetricTagsAreUsed() {
        var store = mock(ReconciliationStore.class);
        var repository = mock(ReconciliationRepository.class);
        var provider = mock(PaymentStatusQueryOperations.class);
        var meters = new SimpleMeterRegistry();
        var first = lease();
        var second = lease();
        when(store.claim(any())).thenReturn(Optional.of(first), Optional.of(second), Optional.empty());
        when(provider.query(first.request())).thenThrow(new IllegalStateException("secret-provider-body"));
        when(store.failure(any(), anyBoolean(), anyString(), any()))
                .thenThrow(new IllegalStateException("database-down"));
        when(provider.query(second.request())).thenReturn(result());
        when(store.complete(any(), any(), any())).thenReturn("applied");
        var processor = new ReconciliationProcessor(
                store,
                repository,
                provider,
                new ReconciliationProperties(),
                meters,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThat(processor.runBatch()).isEqualTo(2);
        assertThat(meters.get("shop.payment.reconciliation.items")
                        .tag("outcome", "record_error")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(meters.get("shop.payment.reconciliation.items")
                        .tag("outcome", "applied")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(meters.getMeters()).allSatisfy(m -> assertThat(m.getId().toString())
                .doesNotContain("secret-provider-body", first.token().toString()));
        meters.close();
    }

    @Test
    void claimAndMetricRefreshFailuresAreContainedAndSchedulerRecordsFailure() {
        var store = mock(ReconciliationStore.class);
        var repository = mock(ReconciliationRepository.class);
        var meters = new SimpleMeterRegistry();
        when(store.claim(any())).thenThrow(new IllegalStateException("database unavailable"));
        when(repository.manualCount()).thenThrow(new IllegalStateException("database unavailable"));
        var processor = new ReconciliationProcessor(
                store,
                repository,
                mock(PaymentStatusQueryOperations.class),
                new ReconciliationProperties(),
                meters,
                Clock.systemUTC());
        assertThat(processor.runBatch()).isZero();
        assertThat(meters.get("shop.payment.reconciliation.items")
                        .tag("outcome", "claim_error")
                        .counter()
                        .count())
                .isEqualTo(1);
        var jobProcessor = mock(ReconciliationProcessor.class);
        var job = new ReconciliationJob(jobProcessor, meters);
        job.run();
        when(jobProcessor.runBatch()).thenThrow(new IllegalStateException("no raw cause in logs"));
        job.run();
        assertThat(meters.get("shop.payment.reconciliation.runs")
                        .tag("outcome", "success")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(meters.get("shop.payment.reconciliation.runs")
                        .tag("outcome", "failed")
                        .counter()
                        .count())
                .isEqualTo(1);
        meters.close();
    }

    private ApplicationContextRunner runner(String code) {
        var provider = mock(PaymentStatusQueryOperations.class);
        when(provider.providerCode()).thenReturn(code);
        return new ApplicationContextRunner()
                .withUserConfiguration(ReconciliationConfiguration.class)
                .withPropertyValues("app.payment.reconciliation.enabled=true")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(PaymentAttemptRepository.class, () -> mock(PaymentAttemptRepository.class))
                .withBean(PaymentLifecycleEventPublisher.class, () -> mock(PaymentLifecycleEventPublisher.class))
                .withBean(PaymentStatusQueryOperations.class, () -> provider)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
    }

    private ReconciliationLease lease() {
        var request = new PaymentStatusQueryRequest(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "VND", Instant.EPOCH);
        return new ReconciliationLease(request.queryId(), 0, 1, Instant.EPOCH, Instant.EPOCH.plusSeconds(30), request);
    }

    private PaymentStatusQueryResult result() {
        return new PaymentStatusQueryResult(
                PaymentProviderStatus.SUCCEEDED, "ref", "1", Instant.EPOCH, "response1", "a".repeat(64));
    }
}
