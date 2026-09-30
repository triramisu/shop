package com.shop.inventory.internal.entity;

import com.shop.inventory.internal.constant.InventoryTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = InventoryTableNames.STOCK_RESERVATIONS)
public class StockReservation {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false, updatable = false)
    private StockItem stockItem;

    @Column(nullable = false, updatable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockReservationStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockReservation() {}

    @Builder(access = AccessLevel.PRIVATE)
    private StockReservation(UUID id, StockItem stockItem, long quantity, Instant expiresAt, Instant issuedAt) {
        this.id = Objects.requireNonNull(id, "reservation id is required");
        this.stockItem = Objects.requireNonNull(stockItem, "stock item is required");
        this.quantity = InventoryDomainRules.positive(quantity, "reservation quantity");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiration is required");
        this.createdAt = Objects.requireNonNull(issuedAt, "issued at is required");
        this.updatedAt = issuedAt;
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiration must be after issue time");
        }
        status = StockReservationStatus.RESERVED;
    }

    public static StockReservation issue(
            UUID id, StockItem stockItem, long quantity, Instant expiresAt, Instant issuedAt) {
        return StockReservation.builder()
                .id(id)
                .stockItem(stockItem)
                .quantity(quantity)
                .expiresAt(expiresAt)
                .issuedAt(issuedAt)
                .build();
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = Instant.now();
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }
}
