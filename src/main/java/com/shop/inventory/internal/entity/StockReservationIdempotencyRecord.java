package com.shop.inventory.internal.entity;

import com.shop.inventory.internal.constant.InventoryTableNames;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.inventory.reservation.StockReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = InventoryTableNames.STOCK_RESERVATION_IDEMPOTENCY,
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_ton_kho_luy_dang_reservation_operation",
                        columnNames = {"reservation_id", "operation"}))
public class StockReservationIdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "reservation_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private StockReservationOperation operation;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, updatable = false, length = 20)
    private StockReservationIdempotencyStatus processingStatus;

    @Column(name = "result_stock_item_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID resultStockItemId;

    @Column(name = "result_quantity", nullable = false, updatable = false)
    private long resultQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, updatable = false, length = 20)
    private StockReservationStatus resultStatus;

    @Column(name = "result_expires_at", nullable = false, updatable = false)
    private Instant resultExpiresAt;

    @Column(name = "result_on_hand", nullable = false, updatable = false)
    private long resultOnHand;

    @Column(name = "result_reserved", nullable = false, updatable = false)
    private long resultReserved;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected StockReservationIdempotencyRecord() {}

    @Builder(access = AccessLevel.PRIVATE)
    private StockReservationIdempotencyRecord(
            UUID reservationId,
            StockReservationOperation operation,
            String requestFingerprint,
            StockReservationResult result,
            Instant completedAt) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservation id is required");
        this.operation = Objects.requireNonNull(operation, "reservation operation is required");
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint, "request fingerprint is required");
        if (requestFingerprint.length() != 64) {
            throw new IllegalArgumentException("request fingerprint must be a SHA-256 hex value");
        }
        StockReservationResult storedResult = Objects.requireNonNull(result, "reservation result is required");
        if (!reservationId.equals(storedResult.reservationId())) {
            throw new IllegalArgumentException("reservation result id does not match idempotency key");
        }
        processingStatus = StockReservationIdempotencyStatus.COMPLETED;
        resultStockItemId = storedResult.stockItemId();
        resultQuantity = storedResult.quantity();
        resultStatus = storedResult.status();
        resultExpiresAt = storedResult.expiresAt();
        resultOnHand = storedResult.onHand();
        resultReserved = storedResult.reserved();
        this.completedAt = Objects.requireNonNull(completedAt, "completion time is required");
    }

    public static StockReservationIdempotencyRecord complete(
            UUID reservationId,
            StockReservationOperation operation,
            String requestFingerprint,
            StockReservationResult result,
            Instant completedAt) {
        return StockReservationIdempotencyRecord.builder()
                .reservationId(reservationId)
                .operation(operation)
                .requestFingerprint(requestFingerprint)
                .result(result)
                .completedAt(completedAt)
                .build();
    }

    public boolean hasFingerprint(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public StockReservationResult toResult() {
        return new StockReservationResult(
                reservationId,
                resultStockItemId,
                resultQuantity,
                resultStatus,
                resultExpiresAt,
                resultOnHand,
                resultReserved,
                resultOnHand - resultReserved);
    }
}
