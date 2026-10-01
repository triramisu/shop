package com.shop.order.internal.checkout.orchestration;

import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import com.shop.order.internal.constant.OrderTableNames;
import com.shop.order.internal.entity.CustomerOrder;
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
        updatedAt = Objects.requireNonNull(occurredAt, "occurrence time is required");
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
        updatedAt = Objects.requireNonNull(occurredAt, "occurrence time is required");
    }

    public void beginCompensation(String code, Instant occurredAt) {
        requireProcessing();
        status = InventoryOrchestrationStatus.COMPENSATING;
        failureCode = requireFailureCode(code);
        updatedAt = Objects.requireNonNull(occurredAt, "occurrence time is required");
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
        updatedAt = Objects.requireNonNull(occurredAt, "occurrence time is required");
    }

    public void completeFailed(Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.COMPENSATING
                || lines.stream().anyMatch(line -> line.getStatus() == InventoryReservationLineStatus.RESERVED)) {
            throw new IllegalStateException("inventory compensation is incomplete");
        }
        status = InventoryOrchestrationStatus.FAILED;
        updatedAt = Objects.requireNonNull(occurredAt, "occurrence time is required");
    }

    public void markCompensationRequired(Instant occurredAt) {
        if (status != InventoryOrchestrationStatus.COMPENSATING
                || lines.stream().noneMatch(line -> line.getStatus() == InventoryReservationLineStatus.RESERVED)) {
            throw new IllegalStateException("no unresolved inventory compensation exists");
        }
        status = InventoryOrchestrationStatus.COMPENSATION_REQUIRED;
        updatedAt = Objects.requireNonNull(occurredAt, "occurrence time is required");
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
