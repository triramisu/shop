package com.shop.inventory.internal.entity;

import com.shop.inventory.internal.constant.InventoryTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = InventoryTableNames.STOCK_ITEMS)
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "product_variant_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(nullable = false, updatable = false, length = 100)
    private String sku;

    @Column(name = "location_code", nullable = false, updatable = false, length = 64)
    private String locationCode;

    @Column(name = "on_hand", nullable = false)
    private long onHand;

    @Column(name = "reserved_quantity", nullable = false)
    private long reserved;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockItem() {}

    @Builder(access = AccessLevel.PRIVATE)
    private StockItem(UUID productVariantId, String sku, String locationCode, long initialQuantity) {
        this.productVariantId = Objects.requireNonNull(productVariantId, "product variant id is required");
        this.sku = InventoryDomainRules.sku(sku);
        this.locationCode = InventoryDomainRules.locationCode(locationCode);
        this.onHand = InventoryDomainRules.nonNegative(initialQuantity, "initial quantity");
    }

    public static StockItem create(UUID productVariantId, String sku, String locationCode, long initialQuantity) {
        return StockItem.builder()
                .productVariantId(productVariantId)
                .sku(sku)
                .locationCode(locationCode)
                .initialQuantity(initialQuantity)
                .build();
    }

    public long getAvailable() {
        return onHand - reserved;
    }

    public void adjustOnHand(long delta) {
        if (delta == 0) {
            throw new IllegalArgumentException("quantity delta must not be zero");
        }
        long adjusted = InventoryDomainRules.addExact(onHand, delta, "on hand quantity");
        if (adjusted < reserved) {
            throw new IllegalStateException("on hand quantity cannot be lower than reserved quantity");
        }
        onHand = adjusted;
    }

    public void reserve(long quantity) {
        long requiredQuantity = InventoryDomainRules.positive(quantity, "reservation quantity");
        if (requiredQuantity > getAvailable()) {
            throw new IllegalStateException("available quantity is insufficient");
        }
        reserved = InventoryDomainRules.addExact(reserved, requiredQuantity, "reserved quantity");
    }

    public void release(long quantity) {
        long releasedQuantity = InventoryDomainRules.positive(quantity, "release quantity");
        if (releasedQuantity > reserved) {
            throw new IllegalStateException("release quantity exceeds reserved quantity");
        }
        reserved -= releasedQuantity;
    }

    public void confirm(long quantity) {
        long confirmedQuantity = InventoryDomainRules.positive(quantity, "confirmed quantity");
        if (confirmedQuantity > reserved) {
            throw new IllegalStateException("confirmed quantity exceeds reserved quantity");
        }
        reserved -= confirmedQuantity;
        onHand -= confirmedQuantity;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }
}
