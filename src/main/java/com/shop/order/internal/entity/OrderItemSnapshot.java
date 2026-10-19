package com.shop.order.internal.entity;

import com.shop.order.internal.constant.OrderTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Getter
@Entity
@Immutable
@Table(name = OrderTableNames.ORDER_ITEMS)
public class OrderItemSnapshot {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private CustomerOrder order;

    @Column(name = "product_variant_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(nullable = false, updatable = false, length = 100)
    private String sku;

    @Column(name = "product_name", nullable = false, updatable = false, length = 200)
    private String productName;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "subtotal_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal discount;

    @Column(name = "tax_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal tax;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal total;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderItemSnapshot() {}

    OrderItemSnapshot(CustomerOrder order, int lineNumber, OrderItemSnapshotDraft draft, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.order = Objects.requireNonNull(order, "order is required");
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("line number must be positive");
        }
        this.lineNumber = lineNumber;
        OrderItemSnapshotDraft validated = Objects.requireNonNull(draft, "snapshot draft is required");
        this.productVariantId = validated.productVariantId();
        this.sku = validated.sku();
        this.productName = validated.productName();
        this.quantity = validated.quantity();
        this.unitPrice = validated.unitPrice();
        this.subtotal = validated.subtotal();
        this.discount = validated.discount();
        this.tax = validated.tax();
        this.total = validated.total();
        this.currency = validated.currency();
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
    }
}
