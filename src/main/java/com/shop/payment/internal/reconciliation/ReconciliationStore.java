package com.shop.payment.internal.reconciliation;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.provider.vnpay.VnpayPaymentProvider;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.service.PaymentLifecycleEventPublisher;
import com.shop.payment.provider.PaymentProviderStatus;
import com.shop.payment.provider.PaymentStatusQueryRequest;
import com.shop.payment.provider.PaymentStatusQueryResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class ReconciliationStore {
    private final ReconciliationRepository repository;
    private final PaymentAttemptRepository attempts;
    private final PaymentLifecycleEventPublisher publisher;
    private final ReconciliationProperties properties;

    ReconciliationStore(
            ReconciliationRepository repository,
            PaymentAttemptRepository attempts,
            PaymentLifecycleEventPublisher publisher,
            ReconciliationProperties properties) {
        this.repository = repository;
        this.attempts = attempts;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Optional<ReconciliationLease> claim(Instant now) {
        if (repository.lockProvider().isAfter(now)) return Optional.empty();
        for (int i = 0; i < properties.getBatchSize(); i++) {
            var id = repository.candidate(now, now.minus(properties.getMinimumAge()));
            if (id.isEmpty()) return Optional.empty();
            var payment = attempts.findForProviderUpdate(id.get()).orElseThrow();
            repository.seed(payment, now, payment.getCreatedAt().plus(properties.getMaximumAge()));
            var row = repository.lock(payment.getId());
            // Candidate selection is not a lock: a completing worker can change this row while
            // we wait for the payment lock. Recheck eligibility under both locks.
            if ((!row.state().equals("READY") && !row.state().equals("LEASED"))
                    || (row.state().equals("READY") && row.nextQueryAt().isAfter(now))
                    || (row.state().equals("LEASED") && row.until().isAfter(now))) {
                continue;
            }
            UUID token = row.token() == null ? UUID.randomUUID() : row.token();
            if (row.token() != null) {
                repository.append(
                        row.id(),
                        token,
                        row.count(),
                        "LEASE_EXPIRED",
                        "LEASE_TIMEOUT",
                        null,
                        false,
                        null,
                        row.started(),
                        now);
            }
            if (payment.getStatus().isTerminal() || exhausted(row, now) || !valid(payment)) {
                String state = payment.getStatus().isTerminal() ? "DONE" : "MANUAL";
                String error = payment.getStatus().isTerminal() ? "ALREADY_TERMINAL" : "LIMIT_OR_INVALID_PAYMENT";
                repository.finish(row.id(), state, error, now, now);
                if (row.token() == null)
                    repository.append(row.id(), token, row.count(), state, error, null, false, null, now, now);
                continue;
            }
            UUID newToken = UUID.randomUUID();
            var request = new PaymentStatusQueryRequest(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getCreatedAt(),
                    newToken);
            var lease = new ReconciliationLease(
                    newToken,
                    payment.getVersion(),
                    row.count() + 1,
                    now,
                    now.plus(properties.getLeaseDuration()),
                    request);
            repository.claim(lease, now);
            repository.pace(now.plus(properties.getProviderInterval()));
            return Optional.of(lease);
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public String complete(ReconciliationLease lease, PaymentStatusQueryResult result, Instant now) {
        PaymentAttempt payment = attempts.findForProviderUpdate(lease.request().paymentAttemptId())
                .orElseThrow();
        var row = repository.lock(payment.getId());
        if (!owns(row, lease)) return "lost_lease";
        boolean freshReceipt = VnpayPaymentProvider.reference(payment.getId()).equals(result.providerReference())
                && !repository.seen(result.receiptHash());
        if (payment.getStatus().isTerminal()) {
            repository.finish(row.id(), "DONE", "ALREADY_TERMINAL", now, now);
            audit(row, lease, "STALE", "ALREADY_TERMINAL", result, freshReceipt, null, now);
            return "stale";
        }
        if (!row.until().isAfter(now)
                || payment.getVersion() != row.paymentVersion()
                || lease.paymentVersion() != row.paymentVersion()
                || result.observedAt().isBefore(row.started())
                || result.observedAt().isAfter(now)
                || Duration.between(row.started(), now).compareTo(Duration.ofSeconds(10)) > 0) {
            return defer(row, lease, "STALE", "STALE_OBSERVATION", result, freshReceipt, false, now);
        }
        if (!valid(payment) || !payment.getProviderReference().equals(result.providerReference())) {
            return defer(row, lease, "REJECTED", "RESULT_MISMATCH", result, false, true, now);
        }
        if (!freshReceipt) {
            return defer(row, lease, "REPLAY", "RECEIPT_REPLAY", result, false, false, now);
        }
        if (result.status() != PaymentProviderStatus.SUCCEEDED && result.status() != PaymentProviderStatus.FAILED) {
            return defer(row, lease, "DEFERRED", "PROVIDER_UNCERTAIN", result, true, false, now);
        }
        var event = payment.transition(
                result.status() == PaymentProviderStatus.SUCCEEDED ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED,
                result.providerReference(),
                null,
                result.status() == PaymentProviderStatus.FAILED ? "VNPAY_QUERY_FAILED" : null,
                now.isBefore(payment.getUpdatedAt()) ? payment.getUpdatedAt() : now);
        attempts.saveAndFlush(payment);
        repository.finish(row.id(), "DONE", null, now, now);
        audit(
                row,
                lease,
                result.status() == PaymentProviderStatus.SUCCEEDED ? "APPLIED_SUCCESS" : "APPLIED_FAILURE",
                null,
                result,
                true,
                event.eventId(),
                now);
        publisher.publish(event);
        return "applied";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public String failure(ReconciliationLease lease, boolean permanent, String error, Instant now) {
        var payment = attempts.findForProviderUpdate(lease.request().paymentAttemptId())
                .orElseThrow();
        var row = repository.lock(payment.getId());
        if (!owns(row, lease)) return "lost_lease";
        if (payment.getStatus().isTerminal()) {
            repository.finish(row.id(), "DONE", "ALREADY_TERMINAL", now, now);
            audit(row, lease, "STALE", "ALREADY_TERMINAL", null, false, null, now);
            return "stale";
        }
        return defer(row, lease, "QUERY_ERROR", error, null, false, permanent, now);
    }

    private String defer(
            ReconciliationRepository.Row row,
            ReconciliationLease lease,
            String outcome,
            String error,
            PaymentStatusQueryResult result,
            boolean newReceipt,
            boolean permanent,
            Instant now) {
        boolean manual = permanent || exhausted(row, now);
        repository.finish(
                row.id(), manual ? "MANUAL" : "READY", error, now.plus(properties.backoff(row.id(), row.count())), now);
        audit(row, lease, manual ? "MANUAL" : outcome, error, result, newReceipt, null, now);
        return manual ? "manual" : "deferred";
    }

    private void audit(
            ReconciliationRepository.Row row,
            ReconciliationLease lease,
            String outcome,
            String error,
            PaymentStatusQueryResult result,
            boolean newReceipt,
            UUID eventId,
            Instant now) {
        repository.append(
                row.id(),
                lease.token(),
                row.count(),
                outcome,
                error,
                result,
                newReceipt,
                eventId,
                lease.startedAt(),
                now);
    }

    private boolean exhausted(ReconciliationRepository.Row row, Instant now) {
        return row.count() >= properties.getMaxAttempts() || !row.deadline().isAfter(now);
    }

    private static boolean owns(ReconciliationRepository.Row row, ReconciliationLease lease) {
        return row.state().equals("LEASED") && lease.token().equals(row.token());
    }

    private static boolean valid(PaymentAttempt payment) {
        return payment.getProviderCode().equals("VNPAY")
                && payment.getCurrency().equals("VND")
                && VnpayPaymentProvider.reference(payment.getId()).equals(payment.getProviderReference())
                && payment.getAmount().stripTrailingZeros().scale() <= 0
                && payment.getAmount().precision() <= 12;
    }
}
