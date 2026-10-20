package com.shop.payment.internal.reconciliation;

import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentStatusQueryOperations;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class ReconciliationProcessor {
    private final ReconciliationStore store;
    private final ReconciliationRepository repository;
    private final PaymentStatusQueryOperations provider;
    private final ReconciliationProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;
    private final AtomicLong manual = new AtomicLong();
    private final AtomicLong oldest = new AtomicLong();

    ReconciliationProcessor(
            ReconciliationStore store,
            ReconciliationRepository repository,
            PaymentStatusQueryOperations provider,
            ReconciliationProperties properties,
            MeterRegistry meters,
            Clock clock) {
        this.store = store;
        this.repository = repository;
        this.provider = provider;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
        meters.gauge("shop.payment.reconciliation.manual", manual);
        meters.gauge("shop.payment.reconciliation.oldest.pending.seconds", oldest);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int runBatch() {
        int selected = 0;
        for (int i = 0; i < properties.getBatchSize(); i++) {
            ReconciliationLease lease;
            try {
                var claimed = store.claim(clock.instant());
                if (claimed.isEmpty()) break;
                lease = claimed.get();
            } catch (RuntimeException error) {
                item("claim_error");
                break;
            }
            selected++;
            String outcome;
            try {
                var result = provider.query(lease.request());
                outcome = store.complete(lease, result, clock.instant());
            } catch (RuntimeException error) {
                try {
                    boolean permanent = error instanceof PaymentProviderException failure && !failure.isRetryable();
                    // Finite taxonomy only; never log provider message, raw body or persistence exception cause.
                    outcome = store.failure(
                            lease,
                            permanent,
                            permanent ? "PROVIDER_PERMANENT" : "QUERY_OR_PERSISTENCE_ERROR",
                            clock.instant());
                } catch (RuntimeException recordingFailure) {
                    // Keep the lease for expiry/reclaim; do not synthesize a payment outcome.
                    outcome = "record_error";
                }
            }
            item(outcome);
        }
        refreshGauges();
        return selected;
    }

    private void refreshGauges() {
        try {
            manual.set(repository.manualCount());
            var first = repository.oldestPending();
            oldest.set(
                    first == null
                            ? 0
                            : Math.max(
                                    0, Duration.between(first, clock.instant()).toSeconds()));
        } catch (RuntimeException error) {
            item("metric_refresh_error");
        }
    }

    private void item(String outcome) {
        meters.counter("shop.payment.reconciliation.items", "outcome", outcome).increment();
    }
}
