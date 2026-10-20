package com.shop.payment.internal.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.provider.vnpay.VnpaySignature;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.service.PaymentLifecycleEventPublisher;
import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderStatus;
import com.shop.payment.provider.PaymentStatusQueryOperations;
import com.shop.payment.provider.PaymentStatusQueryRequest;
import com.shop.payment.provider.PaymentStatusQueryResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:payment-reconciliation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "app.payment.provider.type=vnpay",
            "app.payment.vnpay.tmn-code=TEST1234",
            "app.payment.vnpay.hash-secret=reconciliation-test-signature-key-0123456789",
            "app.payment.vnpay.public-base-url=https://api.example.com",
            "app.payment.vnpay.allowed-hosts=api.example.com",
            "app.payment.vnpay.query.enabled=true",
            "app.payment.vnpay.query.server-ip=203.0.113.10",
            "app.payment.reconciliation.enabled=true",
            "app.payment.reconciliation.initial-delay=5m",
            "app.payment.reconciliation.fixed-delay=5m"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(ReconciliationIntegrationSupport.Ports.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class ReconciliationIntegrationSupport {
    @Autowired
    ReconciliationStore store;

    @Autowired
    ReconciliationProcessor processor;

    @Autowired
    ReconciliationProperties properties;

    @Autowired
    PaymentAttemptRepository payments;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    ScriptedProvider provider;

    @Autowired
    MeterRegistry meters;

    @Autowired
    MockMvc mvc;

    @Autowired
    VnpaySignature signer;

    @MockitoBean(name = "paymentReconciliationClock")
    Clock clock;

    @MockitoSpyBean
    PaymentLifecycleEventPublisher publisher;

    Instant now;

    @BeforeEach
    void isolateTestFixtures() {
        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        when(clock.instant()).thenAnswer(call -> now);
        // Only the dedicated disposable test database; never delete payment history.
        jdbc.update("DELETE FROM thanh_toan_lich_su_doi_soat");
        jdbc.update("DELETE FROM thanh_toan_doi_soat");
        jdbc.update(
                "UPDATE thanh_toan_nhip_doi_soat SET next_claim_at=? WHERE provider_code='VNPAY'",
                ReconciliationRepository.time(Instant.parse("2000-01-01T00:00:00Z")));
        jdbc.update("""
                UPDATE thanh_toan_lan_thu SET status='CANCELLED', action_url=NULL, failure_code=NULL,
                updated_at=GREATEST(updated_at,?),completed_at=GREATEST(updated_at,?),version=version+1
                WHERE provider_code='VNPAY' AND status IN ('REQUIRES_ACTION','PENDING','UNKNOWN')
                """, ReconciliationRepository.time(now), ReconciliationRepository.time(now));
        properties.setMaxAttempts(12);
        provider.action = request -> {
            throw new AssertionError("unexpected real query invocation");
        };
        reset(publisher);
        doCallRealMethod().when(publisher).publish(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentProviderStatus.class,
            names = {"SUCCEEDED", "FAILED"})
    void appliesDefinitiveResultExactlyOnce(PaymentProviderStatus status) {
        var payment = payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        assertThat(store.complete(lease, result(lease, status, "a"), now)).isEqualTo("applied");
        assertThat(store.complete(lease, result(lease, status, "a"), now)).isEqualTo("lost_lease");
        var stored = payments.findById(payment.getId()).orElseThrow();
        assertThat(stored.getStatus().name()).isEqualTo(status.name());
        assertThat(stored.getVersion()).isEqualTo(payment.getVersion() + 1);
        assertThat(state(payment.getId())).isEqualTo("DONE");
        assertThat(history(payment.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM thanh_toan_lich_su_doi_soat WHERE event_id IS NOT NULL", Integer.class))
                .isEqualTo(1);
        verify(publisher, times(1)).publish(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentProviderStatus.class,
            names = {"UNKNOWN", "PENDING"})
    void uncertainResultPreservesCheckoutActionAndRepeatedReceiptCannotApply(PaymentProviderStatus status) {
        var payment = payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        assertThat(store.complete(lease, result(lease, status, "b"), now)).isEqualTo("deferred");
        assertThat(payments.findById(payment.getId()).orElseThrow().getActionUrl())
                .isEqualTo(payment.getActionUrl());
        assertThat(state(payment.getId())).isEqualTo("READY");
        now = now.plusSeconds(180);
        var retry = store.claim(now).orElseThrow();
        assertThat(store.complete(retry, result(retry, PaymentProviderStatus.SUCCEEDED, "b"), now))
                .isEqualTo("deferred");
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(history(payment.getId())).isEqualTo(2);
        verify(publisher, times(0)).publish(any());
    }

    @Test
    void ipnCommittedFirstWinsOverStaleFailureQuery() throws Exception {
        var payment = payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        Map<String, String> fields = new java.util.HashMap<>(Map.of(
                "vnp_TmnCode",
                "TEST1234",
                "vnp_TxnRef",
                payment.getProviderReference(),
                "vnp_TransactionNo",
                "123456",
                "vnp_Amount",
                "19900000",
                "vnp_OrderInfo",
                "Thanh toan don hang " + payment.getOrderId(),
                "vnp_BankCode",
                "NCB",
                "vnp_ResponseCode",
                "00",
                "vnp_TransactionStatus",
                "00"));
        fields.put("vnp_SecureHash", signer.sign(fields));
        var request = get("/api/payments/webhooks/vnpay");
        fields.forEach(request::queryParam);
        mvc.perform(request).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(store.complete(lease, result(lease, PaymentProviderStatus.FAILED, "c"), now))
                .isEqualTo("stale");
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(state(payment.getId())).isEqualTo("DONE");
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void changedNonterminalVersionAndExpiredObservationCannotTransition() {
        var payment = payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            var locked = payments.findForProviderUpdate(payment.getId()).orElseThrow();
            locked.transition(PaymentStatus.UNKNOWN, locked.getProviderReference(), null, now);
            payments.saveAndFlush(locked);
        });
        assertThat(store.complete(lease, result(lease, PaymentProviderStatus.SUCCEEDED, "d"), now))
                .isEqualTo("deferred");
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        now = now.plusSeconds(180);
        var retry = store.claim(now).orElseThrow();
        var proof = result(retry, PaymentProviderStatus.SUCCEEDED, "e");
        now = now.plusSeconds(11);
        assertThat(store.complete(retry, proof, now)).isEqualTo("deferred");
        verify(publisher, times(0)).publish(any());
    }

    @Test
    void abandonedLeaseIsReclaimedAndOldWorkerCannotOverwriteNewLease() {
        var payment = payment(now.minusSeconds(1200));
        var abandoned = store.claim(now).orElseThrow();
        now = now.plusSeconds(31);
        var retry = store.claim(now).orElseThrow();
        assertThat(retry.token()).isNotEqualTo(abandoned.token());
        assertThat(retry.queryCount()).isEqualTo(2);
        assertThat(store.complete(abandoned, result(abandoned, PaymentProviderStatus.SUCCEEDED, "f"), now))
                .isEqualTo("lost_lease");
        assertThat(store.complete(retry, result(retry, PaymentProviderStatus.SUCCEEDED, "a"), now))
                .isEqualTo("applied");
        assertThat(history(payment.getId())).isEqualTo(2);
    }

    @Test
    void maximumAttemptsDeadlineAndPermanentErrorRequireManualWithoutCancellingPayment() {
        var first = payment(now.minusSeconds(1200));
        properties.setMaxAttempts(1);
        var lease = store.claim(now).orElseThrow();
        assertThat(store.failure(lease, false, "TIMEOUT", now)).isEqualTo("manual");
        assertThat(state(first.getId())).isEqualTo("MANUAL");
        assertThat(payments.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        now = now.plusSeconds(6);
        var old = payment(now.minusSeconds(90000));
        assertThat(store.claim(now)).isEmpty();
        assertThat(state(old.getId())).isEqualTo("MANUAL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thanh_toan_lich_su_doi_soat", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void processorCommitsClaimBeforeNetworkAndIsolatesProviderFailure() {
        var payment = payment(now.minusSeconds(1200));
        provider.action = request -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            assertThat(state(payment.getId())).isEqualTo("LEASED");
            assertThat(request.queryId()).isNotNull();
            assertThat(ReconciliationRepository.uuid(jdbc.queryForObject(
                            "SELECT lease_token FROM thanh_toan_doi_soat WHERE payment_attempt_id=?",
                            byte[].class,
                            ReconciliationRepository.bytes(payment.getId()))))
                    .isEqualTo(request.queryId());
            throw new PaymentProviderException(PaymentProviderErrorType.TIMEOUT, "TEST_TIMEOUT");
        };
        Integer selected = new TransactionTemplate(txManager).execute(tx -> processor.runBatch());
        assertThat(selected).isEqualTo(1);
        assertThat(meters.get("shop.payment.reconciliation.oldest.pending.seconds")
                        .gauge()
                        .value())
                .isBetween(1199.0, 1201.0);
        assertThat(state(payment.getId())).isEqualTo("READY");
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        now = now.plusSeconds(180);
        provider.action = request -> {
            throw new PaymentProviderException(PaymentProviderErrorType.AUTHENTICATION_FAILED, "TEST_AUTH");
        };
        assertThat(processor.runBatch()).isEqualTo(1);
        assertThat(state(payment.getId())).isEqualTo("MANUAL");
    }

    @Test
    void transitionAndReceiptRollbackTogetherIfPublicationFails() {
        var payment = payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        doThrow(new IllegalStateException("injected publisher failure"))
                .when(publisher)
                .publish(any());
        assertThatThrownBy(() -> store.complete(lease, result(lease, PaymentProviderStatus.SUCCEEDED, "a"), now))
                .isInstanceOf(IllegalStateException.class);
        assertThat(state(payment.getId())).isEqualTo("LEASED");
        assertThat(history(payment.getId())).isZero();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        doCallRealMethod().when(publisher).publish(any());
        assertThat(store.complete(lease, result(lease, PaymentProviderStatus.SUCCEEDED, "a"), now))
                .isEqualTo("applied");
    }

    @Test
    void twoConcurrentWorkersShareDatabasePacingAndOnlyOneClaims() throws Exception {
        payment(now.minusSeconds(1200));
        payment(now.minusSeconds(1100));
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(
                    threads.submit(() -> {
                        start.await();
                        return store.claim(now);
                    }),
                    threads.submit(() -> {
                        start.await();
                        return store.claim(now);
                    }));
            start.countDown();
            var first = tasks.get(0).get(10, TimeUnit.SECONDS);
            var second = tasks.get(1).get(10, TimeUnit.SECONDS);
            assertThat((first.isPresent() ? 1 : 0) + (second.isPresent() ? 1 : 0))
                    .isEqualTo(1);
            assertThat(store.claim(now.plusSeconds(4))).isEmpty();
            assertThat(store.claim(now.plusSeconds(5))).isPresent();
        }
    }

    @Test
    void concurrentCompletionsCommitOneEventAndReceipt() throws Exception {
        var payment = payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        var proof = result(lease, PaymentProviderStatus.SUCCEEDED, "a");
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> {
                start.await();
                return store.complete(lease, proof, now);
            });
            var second = threads.submit(() -> {
                start.await();
                return store.complete(lease, proof, now);
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("applied", "lost_lease");
        }
        assertThat(history(payment.getId())).isEqualTo(1);
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void minimumAgeMismatchedResultAndExpiredOwnedLeaseNeverFabricateSuccess() {
        var payment = payment(now.minusSeconds(1));
        assertThat(store.claim(now)).isEmpty();
        now = now.plusSeconds(600);
        var first = store.claim(now).orElseThrow();
        var mismatch = new PaymentStatusQueryResult(
                PaymentProviderStatus.SUCCEEDED, "wrongReference", "123456", now, "response1", "a".repeat(64));
        assertThat(store.complete(first, mismatch, now)).isEqualTo("manual");
        assertThat(state(payment.getId())).isEqualTo("MANUAL");
        now = now.plusSeconds(6);
        var other = payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        now = now.plusSeconds(31);
        assertThat(store.complete(lease, result(lease, PaymentProviderStatus.SUCCEEDED, "b"), now))
                .isEqualTo("deferred");
        assertThat(state(other.getId())).isEqualTo("READY");
        assertThat(payments.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        verify(publisher, times(0)).publish(any());
    }

    @Test
    void pastAndFutureObservationsAndForgedVersionAreFenced() {
        payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        var future = new PaymentStatusQueryResult(
                PaymentProviderStatus.SUCCEEDED,
                lease.request().paymentAttemptId().toString().replace("-", ""),
                "1",
                now.plusSeconds(1),
                "response1",
                "a".repeat(64));
        assertThat(store.complete(lease, future, now)).isEqualTo("deferred");
        now = now.plusSeconds(180);
        var next = store.claim(now).orElseThrow();
        var past = new PaymentStatusQueryResult(
                PaymentProviderStatus.SUCCEEDED,
                next.request().paymentAttemptId().toString().replace("-", ""),
                "1",
                now.minusSeconds(1),
                "response2",
                "b".repeat(64));
        assertThat(store.complete(next, past, now)).isEqualTo("deferred");
        now = now.plusSeconds(300);
        var current = store.claim(now).orElseThrow();
        var forged = new ReconciliationLease(
                current.token(),
                current.paymentVersion() + 1,
                current.queryCount(),
                current.startedAt(),
                current.until(),
                current.request());
        assertThat(store.complete(forged, result(current, PaymentProviderStatus.SUCCEEDED, "c"), now))
                .isEqualTo("deferred");
        verify(publisher, times(0)).publish(any());
    }

    @Test
    void manualAlertCountsOnlyUnresolvedPaymentsWithoutDeletingHistoricalCheckpoint() {
        var payment = payment(now.minusSeconds(1200));
        var lease = store.claim(now).orElseThrow();
        store.failure(lease, true, "PROVIDER_PERMANENT", now);
        var metadata = new ReconciliationRepository(jdbc);
        assertThat(metadata.manualCount()).isEqualTo(1);
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            var locked = payments.findForProviderUpdate(payment.getId()).orElseThrow();
            // Same aggregate transition used by a subsequently confirmed IPN.
            locked.transition(PaymentStatus.SUCCEEDED, locked.getProviderReference(), null, now);
            payments.saveAndFlush(locked);
        });
        assertThat(metadata.manualCount()).isZero();
        assertThat(state(payment.getId())).isEqualTo("MANUAL");
        assertThat(history(payment.getId())).isEqualTo(1);
    }

    PaymentAttempt payment(Instant created) {
        UUID id = UUID.randomUUID();
        var payment =
                PaymentAttempt.start(id, UUID.randomUUID(), 1, new BigDecimal("199000.00"), "VND", "VNPAY", created);
        payment.transition(
                PaymentStatus.REQUIRES_ACTION,
                id.toString().replace("-", ""),
                URI.create("https://api.example.com/checkout"),
                null,
                created);
        return payments.saveAndFlush(payment);
    }

    PaymentStatusQueryResult result(ReconciliationLease lease, PaymentProviderStatus status, String hashCharacter) {
        return new PaymentStatusQueryResult(
                status,
                lease.request().paymentAttemptId().toString().replace("-", ""),
                "123456",
                now,
                "response1",
                hashCharacter.repeat(64));
    }

    String state(UUID id) {
        return jdbc.queryForObject(
                "SELECT state FROM thanh_toan_doi_soat WHERE payment_attempt_id=?",
                String.class,
                ReconciliationRepository.bytes(id));
    }

    int history(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thanh_toan_lich_su_doi_soat WHERE payment_attempt_id=?",
                Integer.class,
                ReconciliationRepository.bytes(id));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Ports {
        @Bean
        @Primary
        ScriptedProvider scriptedProvider() {
            return new ScriptedProvider();
        }
    }

    static class ScriptedProvider implements PaymentStatusQueryOperations {
        Function<PaymentStatusQueryRequest, PaymentStatusQueryResult> action;

        public String providerCode() {
            return "VNPAY";
        }

        public PaymentStatusQueryResult query(PaymentStatusQueryRequest request) {
            return action.apply(request);
        }
    }
}
