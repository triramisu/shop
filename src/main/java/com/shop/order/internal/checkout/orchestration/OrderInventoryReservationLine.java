package com.shop.order.internal.checkout.orchestration;

import com.shop.order.event.OrderInventoryReservationRequestedLine;
import com.shop.order.internal.constant.OrderTableNames;
import com.shop.order.internal.entity.OrderItemSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;

@Getter
@Entity
@Table(name = OrderTableNames.INVENTORY_RESERVATION_LINES)
public class OrderInventoryReservationLine {

    private static final String OCCURRENCE_TIME_REQUIRED = "occurrence time is required";

    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orchestration_id", nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private OrderInventoryOrchestration orchestration;

    @Column(name = "order_item_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    @Column(name = "product_variant_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(nullable = false, updatable = false, length = 100)
    private String sku;

    @Column(nullable = false, updatable = false)
    private long quantity;

    @Column(name = "stock_item_id", columnDefinition = "BINARY(16)")
    private UUID stockItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryReservationLineStatus status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderInventoryReservationLine() {}

    OrderInventoryReservationLine(
            OrderInventoryOrchestration orchestration, OrderItemSnapshot item, Instant createdAt) {
        this.orchestration = Objects.requireNonNull(orchestration, "inventory orchestration is required");
        OrderItemSnapshot snapshot = Objects.requireNonNull(item, "order item snapshot is required");
        reservationId = snapshot.getId();
        orderItemId = snapshot.getId();
        productVariantId = snapshot.getProductVariantId();
        lineNumber = snapshot.getLineNumber();
        sku = snapshot.getSku();
        quantity = snapshot.getQuantity();
        status = InventoryReservationLineStatus.PENDING;
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
        updatedAt = createdAt;
    }

    public OrderInventoryReservationRequestedLine toEventLine() {
        return new OrderInventoryReservationRequestedLine(reservationId, orderItemId, productVariantId, sku, quantity);
    }

    void markReserved(UUID resolvedStockItemId, Instant occurredAt) {
        if (status != InventoryReservationLineStatus.PENDING && status != InventoryReservationLineStatus.FAILED) {
            throw new IllegalStateException("inventory reservation line cannot be reserved from " + status);
        }
        stockItemId = Objects.requireNonNull(resolvedStockItemId, "stock item id is required");
        status = InventoryReservationLineStatus.RESERVED;
        failureCode = null;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    void markFailed(String code, Instant occurredAt) {
        if (status == InventoryReservationLineStatus.RELEASED) {
            throw new IllegalStateException("released inventory reservation line cannot fail");
        }
        status = InventoryReservationLineStatus.FAILED;
        failureCode = requireFailureCode(code);
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    void markReleased(Instant occurredAt) {
        if (status != InventoryReservationLineStatus.RESERVED) {
            throw new IllegalStateException("only a reserved inventory line can be released");
        }
        status = InventoryReservationLineStatus.RELEASED;
        updatedAt = Objects.requireNonNull(occurredAt, OCCURRENCE_TIME_REQUIRED);
    }

    private String requireFailureCode(String code) {
        if (code == null || code.isBlank() || code.strip().length() > 100) {
            throw new IllegalArgumentException("failure code must contain 1 to 100 characters");
        }
        return code.strip();
    }
}
