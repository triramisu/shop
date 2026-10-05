package com.shop.order.internal.checkout.orchestration;

import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import com.shop.order.internal.constant.OrderTableNames;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.payment.event.PaymentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = OrderTableNames.INVENTORY_ORCHESTRATIONS)
public class OrderInventoryOrchestration {

    private static final String OCCURRENCE_TIME_REQUIRED = "occurrence time is required";

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "request_event_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID requestEventId;

    @Column(name = "correlation_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryOrchestrationStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "payment_attempt_id", columnDefinition = "BINARY(16)")
    private UUID paymentAttemptId;

    @Column(name = "payment_attempt_number")
    private Integer paymentAttemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_payment_status", length = 30)
    private PaymentStatus lastPaymentStatus;

    @Column(name = "last_payment_event_at")
    private Instant lastPaymentEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "orchestration", cascade = CascadeType.ALL)
    @OrderBy("lineNumber ASC")
    @Getter(AccessLevel.NONE)
    private List<OrderInventoryReservationLine> lines = new ArrayList<>();

    protected OrderInventoryOrchestration() {}

    @Builder(access = AccessLevel.PRIVATE)
    private OrderInventoryOrchestration(
            UUID id,
            CustomerOrder order,
            UUID requestEventId,
            UUID correlationId,
            Instant expiresAt,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "orchestration id is required");
        this.order = Objects.requireNonNull(order, "order is required");
        this.requestEventId = Objects.requireNonNull(requestEventId, "request event id is required");
        this.correlationId = Objects.requireNonNull(correlationId, "correlation id is required");
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiration is required");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("orchestration expiration must be in the future");
        }
        status = InventoryOrchestrationStatus.REQUESTED;
        updatedAt = createdAt;
        order.getItems().forEach(item -> lines.add(new OrderInventoryReservationLine(this, item, createdAt)));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("inventory reservation lines are required");
        }
    }

    public static OrderInventoryOrchestration start(
            CustomerOrder order, UUID requestEventId, UUID correlationId, Instant expiresAt, Instant createdAt) {
        return OrderInventoryOrchestration.builder()
                .id(UUID.randomUUID())
                .order(order)
                .requestEventId(requestEventId)
                .correlationId(correlationId)
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();
    }

    public List<OrderInventoryReservationLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public OrderInventoryReservationRequestedEvent toRequestedEvent() {
        return new OrderInventoryReservationRequestedEvent(
                requestEventId,
                correlationId,
                order.getId(),
                expiresAt,
                lines.stream().map(OrderInventoryReservationLine::toEventLine).toList(),
                createdAt);
    }

    public boolean claim(UUID eventId, UUID eventCorrelationId, UUID orderId, Instant occurredAt) {
        requireIdentity(eventId, eventCorrelationId, orderId);
        if (status != InventoryOrchestrationStatus.REQUESTED && status != InventoryOrchestrationStatus.RETRY_REQUIRED) {
            return false;
        }
        status = InventoryOrchestrationStatus.PROCESSING;
        failureCode = null;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
        return true;
    }

    public void markReserved(UUID reservationId, UUID stockItemId, Instant occurredAt) {
        requireProcessing();
        line(reservationId).markReserved(stockItemId, occurredAt);
        updatedAt = occurredAt;
    }

    public void markLineFailed(UUID reservationId, String code, Instant occurredAt) {
        requireProcessing();
        line(reservationId).markFailed(code, occurredAt);
        failureCode = requireFailureCode(code);
        updatedAt = occurredAt;
    }

    public void markRetryRequired(String code, Instant occurredAt) {
        requireProcessing();
        status = InventoryOrchestrationStatus.RETRY_REQUIRED;
        failureCode = requireFailureCode(code);
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    public void beginCompensation(String code, Instant occurredAt) {
        requireProcessing();
        status = InventoryOrchestrationStatus.COMPENSATING;
        failureCode = requireFailureCode(code);
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    public void markReleased(UUID reservationId, Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.COMPENSATING) {
            throw new IllegalStateException("orchestration is not compensating");
        }
        line(reservationId).markReleased(occurredAt);
        updatedAt = occurredAt;
    }

    public void completeReserved(Instant occurredAt) {
        requireProcessing();
        if (lines.stream().anyMatch(line -> line.getStatus() != InventoryReservationLineStatus.RESERVED)) {
            throw new IllegalStateException("all inventory lines must be reserved");
        }
        status = InventoryOrchestrationStatus.RESERVED;
        failureCode = null;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    public void completeFailed(Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.COMPENSATING
                || lines.stream().anyMatch(line -> line.getStatus() == InventoryReservationLineStatus.RESERVED)) {
            throw new IllegalStateException("inventory compensation is incomplete");
        }
        status = InventoryOrchestrationStatus.FAILED;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    public void markCompensationRequired(Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.COMPENSATING
                || lines.stream().noneMatch(line -> line.getStatus() == InventoryReservationLineStatus.RESERVED)) {
            throw new IllegalStateException("no unresolved inventory compensation exists");
        }
        status = InventoryOrchestrationStatus.COMPENSATION_REQUIRED;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    public void assignPaymentAttempt(UUID attemptId, int attemptNumber, Instant occurredAt) {
        UUID requiredAttemptId = Objects.requireNonNull(attemptId, "payment attempt id is required");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("payment attempt number must be positive");
        }
        if (status != InventoryOrchestrationStatus.RESERVED
                && status != InventoryOrchestrationStatus.PAYMENT_PENDING
                && status != InventoryOrchestrationStatus.PAYMENT_RECOVERY_REQUIRED) {
            throw new IllegalStateException("inventory orchestration is not ready for payment");
        }
        if (paymentAttemptId != null
                && (!paymentAttemptId.equals(requiredAttemptId) || !paymentAttemptNumber.equals(attemptNumber))) {
            throw new IllegalStateException("payment attempt identity cannot be changed");
        }
        paymentAttemptId = requiredAttemptId;
        paymentAttemptNumber = attemptNumber;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    public boolean isStalePaymentEvent(PaymentStatus paymentStatus, Instant eventOccurredAt) {
        Objects.requireNonNull(paymentStatus, "payment status is required");
        Instant occurrenceTime = Objects.requireNonNull(eventOccurredAt, "payment event time is required");
        return lastPaymentEventAt != null && occurrenceTime.isBefore(lastPaymentEventAt);
    }

    public boolean beginPaymentConfirmation(PaymentStatus paymentStatus, Instant eventOccurredAt, Instant handledAt) {
        requirePaymentAttempt();
        if (isStalePaymentEvent(paymentStatus, eventOccurredAt)
                || status == InventoryOrchestrationStatus.PAYMENT_CONFIRMED
                || status == InventoryOrchestrationStatus.PAYMENT_RELEASING
                || status == InventoryOrchestrationStatus.PAYMENT_RELEASED) {
            return false;
        }
        if (paymentStatus != PaymentStatus.SUCCEEDED) {
            throw new IllegalArgumentException("payment confirmation requires a successful payment");
        }
        status = InventoryOrchestrationStatus.PAYMENT_CONFIRMING;
        recordPaymentEvent(paymentStatus, eventOccurredAt, handledAt);
        return true;
    }

    public boolean beginPaymentRelease(PaymentStatus paymentStatus, Instant eventOccurredAt, Instant handledAt) {
        requirePaymentAttempt();
        if (isStalePaymentEvent(paymentStatus, eventOccurredAt)
                || status == InventoryOrchestrationStatus.PAYMENT_RELEASED
                || status == InventoryOrchestrationStatus.PAYMENT_CONFIRMING
                || status == InventoryOrchestrationStatus.PAYMENT_CONFIRMED) {
            return false;
        }
        if (paymentStatus != PaymentStatus.FAILED
                && paymentStatus != PaymentStatus.CANCELLED
                && paymentStatus != PaymentStatus.EXPIRED) {
            throw new IllegalArgumentException("payment release requires a terminal unsuccessful payment");
        }
        status = InventoryOrchestrationStatus.PAYMENT_RELEASING;
        recordPaymentEvent(paymentStatus, eventOccurredAt, handledAt);
        return true;
    }

    public boolean recordPaymentPending(PaymentStatus paymentStatus, Instant eventOccurredAt, Instant handledAt) {
        requirePaymentAttempt();
        if (isStalePaymentEvent(paymentStatus, eventOccurredAt)
                || status == InventoryOrchestrationStatus.PAYMENT_CONFIRMING
                || status == InventoryOrchestrationStatus.PAYMENT_CONFIRMED
                || status == InventoryOrchestrationStatus.PAYMENT_RELEASING
                || status == InventoryOrchestrationStatus.PAYMENT_RELEASED) {
            return false;
        }
        if (paymentStatus != PaymentStatus.PENDING
                && paymentStatus != PaymentStatus.REQUIRES_ACTION
                && paymentStatus != PaymentStatus.UNKNOWN) {
            throw new IllegalArgumentException("payment status is not pending");
        }
        status = InventoryOrchestrationStatus.PAYMENT_PENDING;
        recordPaymentEvent(paymentStatus, eventOccurredAt, handledAt);
        return true;
    }

    public void markPaymentRecoveryRequired(Instant occurredAt) {
        if (status == InventoryOrchestrationStatus.PAYMENT_CONFIRMED
                || status == InventoryOrchestrationStatus.PAYMENT_RELEASED) {
            throw new IllegalStateException("a completed payment orchestration cannot require recovery");
        }
        status = InventoryOrchestrationStatus.PAYMENT_RECOVERY_REQUIRED;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    public void markPaymentConfirmed(UUID reservationId, Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.PAYMENT_CONFIRMING) {
            throw new IllegalStateException("payment inventory confirmation is not active");
        }
        line(reservationId).markConfirmed(occurredAt);
        updatedAt = occurredAt;
    }

    public void completePaymentConfirmation(Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.PAYMENT_CONFIRMING
                || lines.stream().anyMatch(line -> line.getStatus() != InventoryReservationLineStatus.CONFIRMED)) {
            throw new IllegalStateException("payment inventory confirmation is incomplete");
        }
        status = InventoryOrchestrationStatus.PAYMENT_CONFIRMED;
        failureCode = null;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    public void markPaymentReleased(UUID reservationId, Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.PAYMENT_RELEASING) {
            throw new IllegalStateException("payment inventory release is not active");
        }
        line(reservationId).markReleased(occurredAt);
        updatedAt = occurredAt;
    }

    public void completePaymentRelease(Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.PAYMENT_RELEASING
                || lines.stream().anyMatch(line -> line.getStatus() != InventoryReservationLineStatus.RELEASED)) {
            throw new IllegalStateException("payment inventory release is incomplete");
        }
        status = InventoryOrchestrationStatus.PAYMENT_RELEASED;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    private void requirePaymentAttempt() {
        if (paymentAttemptId == null || paymentAttemptNumber == null) {
            throw new IllegalStateException("payment attempt has not been assigned");
        }
    }

    private void recordPaymentEvent(PaymentStatus paymentStatus, Instant eventOccurredAt, Instant handledAt) {
        lastPaymentStatus = Objects.requireNonNull(paymentStatus, "payment status is required");
        lastPaymentEventAt = Objects.requireNonNull(eventOccurredAt, "payment event time is required");
        updatedAt = Objects.requireNonNull(handledAt, OCCURRENCE_TIME_REQUIRED);
    }

    public void resumeCompensation(Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.COMPENSATING
                && status != InventoryOrchestrationStatus.COMPENSATION_REQUIRED) {
            throw new IllegalStateException("inventory orchestration does not require compensation");
        }
        status = InventoryOrchestrationStatus.COMPENSATING;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    private void requireIdentity(UUID eventId, UUID eventCorrelationId, UUID orderId) {
        if (!requestEventId.equals(eventId)
                || !correlationId.equals(eventCorrelationId)
                || !order.getId().equals(orderId)) {
            throw new IllegalArgumentException("inventory reservation event identity does not match orchestration");
        }
    }

    private void requireProcessing() {
        if (status != InventoryOrchestrationStatus.PROCESSING) {
            throw new IllegalStateException("inventory orchestration is not processing");
        }
    }

    private OrderInventoryReservationLine line(UUID reservationId) {
        return lines.stream()
                .filter(line -> line.getReservationId().equals(reservationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("inventory reservation line is not part of order"));
    }

    private String requireFailureCode(String code) {
        if (code == null || code.isBlank() || code.strip().length() > 100) {
            throw new IllegalArgumentException("failure code must contain 1 to 100 characters");
        }
        return code.strip();
    }
}
