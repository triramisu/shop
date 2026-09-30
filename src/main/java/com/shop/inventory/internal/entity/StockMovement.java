package com.shop.inventory.internal.entity;

import com.shop.inventory.event.StockMovementType;
import com.shop.inventory.internal.constant.InventoryTableNames;
import com.shop.inventory.internal.constant.InventoryValidationConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Getter
@Entity
@Immutable
@Table(name = InventoryTableNames.STOCK_MOVEMENTS)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false, updatable = false)
    private StockItem stockItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 30)
    private StockMovementType movementType;

    @Column(name = "on_hand_delta", nullable = false, updatable = false)
    private long onHandDelta;

    @Column(name = "reserved_delta", nullable = false, updatable = false)
    private long reservedDelta;

    @Column(name = "on_hand_after", nullable = false, updatable = false)
    private long onHandAfter;

    @Column(name = "reserved_after", nullable = false, updatable = false)
    private long reservedAfter;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "reference_id", updatable = false, length = 100)
    private String referenceId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StockMovement() {}

    @Builder(access = AccessLevel.PRIVATE)
    private StockMovement(
            StockItem stockItem,
            StockMovementType movementType,
            long onHandDelta,
            long reservedDelta,
            String reason,
            String referenceId,
            Instant occurredAt) {
        this.stockItem = Objects.requireNonNull(stockItem, "stock item is required");
        this.movementType = Objects.requireNonNull(movementType, "movement type is required");
        if (onHandDelta == 0 && reservedDelta == 0) {
            throw new IllegalArgumentException("a stock movement must change a balance");
        }
        this.onHandDelta = onHandDelta;
        this.reservedDelta = reservedDelta;
        this.onHandAfter = InventoryDomainRules.nonNegative(stockItem.getOnHand(), "on hand after");
        this.reservedAfter = InventoryDomainRules.nonNegative(stockItem.getReserved(), "reserved after");
        if (reservedAfter > onHandAfter) {
            throw new IllegalStateException("reserved balance cannot exceed on hand balance");
        }
        this.reason = InventoryDomainRules.requiredText(
                reason, "movement reason", InventoryValidationConstants.MAX_REASON_LENGTH);
        this.referenceId = InventoryDomainRules.optionalText(
                referenceId, "reference id", InventoryValidationConstants.MAX_REFERENCE_ID_LENGTH);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurred at is required");
    }

    public static StockMovement record(
            StockItem stockItem,
            StockMovementType movementType,
            long onHandDelta,
            long reservedDelta,
            String reason,
            String referenceId,
            Instant occurredAt) {
        return StockMovement.builder()
                .stockItem(stockItem)
                .movementType(movementType)
                .onHandDelta(onHandDelta)
                .reservedDelta(reservedDelta)
                .reason(reason)
                .referenceId(referenceId)
                .occurredAt(occurredAt)
                .build();
    }

    @PrePersist
    void requireOccurredAt() {
        Objects.requireNonNull(occurredAt, "occurred at is required");
    }
}
