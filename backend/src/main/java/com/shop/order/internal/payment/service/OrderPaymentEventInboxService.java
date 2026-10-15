package com.shop.order.internal.payment.service;

import com.shop.order.internal.payment.entity.OrderPaymentEventOutcome;
import com.shop.order.internal.payment.entity.OrderPaymentEventRecord;
import com.shop.order.internal.payment.repository.OrderPaymentEventRepository;
import com.shop.payment.event.PaymentStatusChangedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderPaymentEventInboxService {

    static final List<OrderPaymentEventOutcome> RECOVERABLE_OUTCOMES = List.of(
            OrderPaymentEventOutcome.RECEIVED,
            OrderPaymentEventOutcome.PROCESSING,
            OrderPaymentEventOutcome.RETRY_REQUIRED);

    OrderPaymentEventRepository eventRepository;
    PaymentEventFingerprint fingerprint;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderPaymentEventReceipt receive(PaymentStatusChangedEvent event, Instant receivedAt) {
        String payloadHash = fingerprint.hash(event);
        Optional<OrderPaymentEventRecord> existing = eventRepository.findByEventId(event.eventId());
        if (existing.isPresent()) {
            requireSamePayload(existing.get(), payloadHash);
            return existing.get().getOutcome() == OrderPaymentEventOutcome.RETRY_REQUIRED
                    ? OrderPaymentEventReceipt.process()
                    : OrderPaymentEventReceipt.skip();
        }
        eventRepository.saveAndFlush(OrderPaymentEventRecord.receive(event, payloadHash, receivedAt));
        return OrderPaymentEventReceipt.process();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentStatusChangedEvent> claim(UUID eventId) {
        OrderPaymentEventRecord event = eventRepository
                .findByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("order payment event was not found"));
        if (!event.claim()) {
            return Optional.empty();
        }
        eventRepository.saveAndFlush(event);
        return Optional.of(event.toEvent());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OrderPaymentEventReceipt> resolveConcurrentDuplicate(PaymentStatusChangedEvent event) {
        String payloadHash = fingerprint.hash(event);
        return eventRepository.findByEventId(event.eventId()).map(existing -> {
            requireSamePayload(existing, payloadHash);
            return existing.getOutcome() == OrderPaymentEventOutcome.RETRY_REQUIRED
                    ? OrderPaymentEventReceipt.process()
                    : OrderPaymentEventReceipt.skip();
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID eventId, OrderPaymentEventOutcome outcome, String failureCode, Instant completedAt) {
        OrderPaymentEventRecord event = eventRepository
                .findByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("order payment event was not found"));
        event.complete(outcome, failureCode, completedAt);
        eventRepository.saveAndFlush(event);
    }

    @Transactional(readOnly = true)
    public List<UUID> findRecoveryCandidates(Instant staleBefore, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("payment event recovery batch size must be positive");
        }
        return eventRepository.findRecoveryCandidateEventIds(
                RECOVERABLE_OUTCOMES,
                java.util.Objects.requireNonNull(staleBefore, "payment event stale cutoff is required"),
                PageRequest.of(0, batchSize));
    }

    private void requireSamePayload(OrderPaymentEventRecord existing, String payloadHash) {
        if (!existing.getPayloadHash().equals(payloadHash)) {
            throw new IllegalStateException("duplicate payment event id has a different payload");
        }
    }
}
