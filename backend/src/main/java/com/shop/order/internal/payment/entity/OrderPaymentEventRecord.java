package com.shop.order.internal.payment.entity;

import com.shop.order.internal.constant.OrderTableNames;
import com.shop.payment.event.PaymentStatus;
import com.shop.payment.event.PaymentStatusChangedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = OrderTableNames.PAYMENT_EVENTS)
public class OrderPaymentEventRecord {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID eventId;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(name = "payment_attempt_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID paymentAttemptId;

    @Column(name = "order_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_payment_status", nullable = false, updatable = false, length = 30)
    private PaymentStatus previousPaymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, updatable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "provider_code", nullable = false, updatable = false, length = 50)
    private String providerCode;

    @Column(name = "provider_reference", updatable = false, length = 150)
    private String providerReference;

    @Column(name = "payment_failure_code", updatable = false, length = 100)
    private String paymentFailureCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderPaymentEventOutcome outcome;

    @Column(name = "processing_failure_code", length = 100)
    private String processingFailureCode;

    @Column(name = "event_occurred_at", nullable = false, updatable = false)
    private Instant eventOccurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrderPaymentEventRecord() {}

    @Builder(access = AccessLevel.PRIVATE)
    private OrderPaymentEventRecord(
            UUID id,
            UUID eventId,
            int eventVersion,
            String payloadHash,
            UUID paymentAttemptId,
            UUID orderId,
            PaymentStatus previousPaymentStatus,
            PaymentStatus paymentStatus,
            BigDecimal amount,
            String currency,
            String providerCode,
            String providerReference,
            String paymentFailureCode,
            Instant eventOccurredAt,
            Instant receivedAt) {
        this.id = Objects.requireNonNull(id, "payment event inbox id is required");
        this.eventId = Objects.requireNonNull(eventId, "payment event id is required");
        this.eventVersion = eventVersion;
        this.payloadHash = requireHash(payloadHash);
        this.paymentAttemptId = Objects.requireNonNull(paymentAttemptId, "payment attempt id is required");
        this.orderId = Objects.requireNonNull(orderId, "order id is required");
        this.previousPaymentStatus =
                Objects.requireNonNull(previousPaymentStatus, "previous payment status is required");
        this.paymentStatus = Objects.requireNonNull(paymentStatus, "payment status is required");
        this.amount = Objects.requireNonNull(amount, "payment amount is required");
        this.currency = Objects.requireNonNull(currency, "payment currency is required");
        this.providerCode = Objects.requireNonNull(providerCode, "payment provider code is required");
        this.providerReference = providerReference;
        this.paymentFailureCode = paymentFailureCode;
        this.eventOccurredAt = Objects.requireNonNull(eventOccurredAt, "payment event time is required");
        this.receivedAt = Objects.requireNonNull(receivedAt, "payment event receipt time is required");
        outcome = OrderPaymentEventOutcome.RECEIVED;
    }

    public static OrderPaymentEventRecord receive(
            PaymentStatusChangedEvent event, String payloadHash, Instant receivedAt) {
        return OrderPaymentEventRecord.builder()
                .id(UUID.randomUUID())
                .eventId(event.eventId())
                .eventVersion(event.eventVersion())
                .payloadHash(payloadHash)
                .paymentAttemptId(event.paymentAttemptId())
                .orderId(event.orderId())
                .previousPaymentStatus(event.previousStatus())
                .paymentStatus(event.currentStatus())
                .amount(event.amount())
                .currency(event.currency())
                .providerCode(event.providerCode())
                .providerReference(event.providerReference())
                .paymentFailureCode(event.failureCode())
                .eventOccurredAt(event.occurredAt())
                .receivedAt(receivedAt)
                .build();
    }

    public PaymentStatusChangedEvent toEvent() {
        return new PaymentStatusChangedEvent(
                eventVersion,
                eventId,
                paymentAttemptId,
                orderId,
                previousPaymentStatus,
                paymentStatus,
                amount,
                currency,
                providerCode,
                providerReference,
                paymentFailureCode,
                eventOccurredAt);
    }

    public boolean claim() {
        if (outcome != OrderPaymentEventOutcome.RECEIVED
                && outcome != OrderPaymentEventOutcome.RETRY_REQUIRED
                && outcome != OrderPaymentEventOutcome.PROCESSING) {
            return false;
        }
        outcome = OrderPaymentEventOutcome.PROCESSING;
        processingFailureCode = null;
        processedAt = null;
        return true;
    }

    public void complete(OrderPaymentEventOutcome terminalOutcome, String failureCode, Instant completedAt) {
        if (outcome != OrderPaymentEventOutcome.PROCESSING) {
            throw new IllegalStateException("payment event is not being processed");
        }
        if (terminalOutcome != OrderPaymentEventOutcome.COMPLETED
                && terminalOutcome != OrderPaymentEventOutcome.IGNORED
                && terminalOutcome != OrderPaymentEventOutcome.RETRY_REQUIRED
                && terminalOutcome != OrderPaymentEventOutcome.MANUAL_ACTION_REQUIRED) {
            throw new IllegalArgumentException("payment event completion outcome is invalid");
        }
        outcome = terminalOutcome;
        processingFailureCode = normalizeFailureCode(failureCode);
        processedAt = Objects.requireNonNull(completedAt, "payment event completion time is required");
    }

    private static String requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payment event payload hash is invalid");
        }
        return value;
    }

    private static String normalizeFailureCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 100 || !normalized.matches("[A-Z0-9][A-Z0-9_.-]*")) {
            throw new IllegalArgumentException("payment event processing failure code is invalid");
        }
        return normalized;
    }
}
