package com.shop.order.internal.entity;

import com.shop.order.event.OrderStatus;
import com.shop.order.event.OrderStatusChangedEvent;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import com.shop.order.internal.constant.OrderTableNames;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = OrderTableNames.ORDERS)
public class CustomerOrder {

    private static final int MAX_OWNER_SUBJECT_LENGTH = 100;

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = MAX_OWNER_SUBJECT_LENGTH)
    private String ownerSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("lineNumber ASC")
    @Getter(AccessLevel.NONE)
    private List<OrderItemSnapshot> items = new ArrayList<>();

    protected CustomerOrder() {}

    @Builder(access = AccessLevel.PRIVATE)
    private CustomerOrder(UUID id, String ownerSubject, List<OrderItemSnapshotDraft> itemDrafts, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "order id is required");
        this.ownerSubject = normalizeOwnerSubject(ownerSubject);
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
        addSnapshots(itemDrafts, createdAt);
        status = OrderStatus.PENDING;
        statusChangedAt = createdAt;
        updatedAt = createdAt;
    }

    public static CustomerOrder createPending(
            String ownerSubject, List<OrderItemSnapshotDraft> itemDrafts, Instant createdAt) {
        return CustomerOrder.builder()
                .id(UUID.randomUUID())
                .ownerSubject(ownerSubject)
                .itemDrafts(itemDrafts)
                .createdAt(createdAt)
                .build();
    }

    public List<OrderItemSnapshot> getItems() {
        return Collections.unmodifiableList(items);
    }

    public String getCurrency() {
        return items.isEmpty() ? null : items.getFirst().getCurrency();
    }

    public BigDecimal getSubtotal() {
        return sum(OrderItemSnapshot::getSubtotal);
    }

    public BigDecimal getDiscount() {
        return sum(OrderItemSnapshot::getDiscount);
    }

    public BigDecimal getTax() {
        return sum(OrderItemSnapshot::getTax);
    }

    public BigDecimal getGrandTotal() {
        return sum(OrderItemSnapshot::getTotal);
    }

    public OrderStatusChangedEvent transition(
            OrderTransitionEvent transitionEvent, OrderTransitionActor actor, Instant occurredAt) {
        Instant transitionTime = Objects.requireNonNull(occurredAt, "transition time is required");
        if (transitionTime.isBefore(statusChangedAt)) {
            throw new IllegalArgumentException("transition time cannot be before the current status time");
        }

        OrderStatus previousStatus = status;
        OrderStatus targetStatus = OrderStateMachine.transition(status, transitionEvent, actor);
        status = targetStatus;
        statusChangedAt = transitionTime;
        updatedAt = transitionTime;

        return new OrderStatusChangedEvent(
                UUID.randomUUID(), id, previousStatus, targetStatus, transitionEvent, actor, transitionTime);
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = Instant.now();
            statusChangedAt = createdAt;
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void beforeUpdate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    private static String normalizeOwnerSubject(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("owner subject is required");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_OWNER_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("owner subject is too long");
        }
        return normalized;
    }

    private void addSnapshots(List<OrderItemSnapshotDraft> itemDrafts, Instant snapshotTime) {
        if (itemDrafts == null || itemDrafts.isEmpty()) {
            throw new IllegalArgumentException("order items are required");
        }
        List<OrderItemSnapshotDraft> immutableDrafts = List.copyOf(itemDrafts);
        String currency = immutableDrafts.getFirst().currency();
        for (int index = 0; index < immutableDrafts.size(); index++) {
            OrderItemSnapshotDraft draft = immutableDrafts.get(index);
            if (!currency.equals(draft.currency())) {
                throw new IllegalArgumentException("order items must use one currency");
            }
            items.add(new OrderItemSnapshot(this, index + 1, draft, snapshotTime));
        }
    }

    private BigDecimal sum(java.util.function.Function<OrderItemSnapshot, BigDecimal> amountExtractor) {
        return items.stream().map(amountExtractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
