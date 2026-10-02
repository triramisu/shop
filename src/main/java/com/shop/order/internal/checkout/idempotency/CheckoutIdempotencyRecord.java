package com.shop.order.internal.checkout.idempotency;

import com.shop.order.internal.constant.OrderTableNames;
import com.shop.shared.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = OrderTableNames.CHECKOUT_IDEMPOTENCY,
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_don_hang_luy_dang_scope",
                    columnNames = {"owner_subject", "operation", "idempotency_key_hash"}),
            @UniqueConstraint(name = "uk_don_hang_luy_dang_execution", columnNames = "execution_id"),
            @UniqueConstraint(name = "uk_don_hang_luy_dang_order", columnNames = "result_order_id")
        })
public class CheckoutIdempotencyRecord {

    static final int HASH_LENGTH = 64;

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = 100)
    private String ownerSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private CheckoutIdempotencyOperation operation;

    @Column(name = "idempotency_key_hash", nullable = false, updatable = false, length = HASH_LENGTH)
    private String idempotencyKeyHash;

    @Column(name = "request_fingerprint", nullable = false, length = HASH_LENGTH)
    private String requestFingerprint;

    @Column(name = "execution_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckoutIdempotencyStatus status;

    @Column(name = "result_order_id", columnDefinition = "BINARY(16)")
    private UUID resultOrderId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CheckoutIdempotencyRecord() {}

    @Builder(access = AccessLevel.PRIVATE)
    private CheckoutIdempotencyRecord(
            UUID id,
            String ownerSubject,
            CheckoutIdempotencyOperation operation,
            String idempotencyKeyHash,
            String requestFingerprint,
            UUID executionId,
            Instant expiresAt,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "idempotency record id is required");
        this.ownerSubject = requireText(ownerSubject, "owner subject", 100);
        this.operation = Objects.requireNonNull(operation, "idempotency operation is required");
        this.idempotencyKeyHash = requireHash(idempotencyKeyHash, "idempotency key hash");
        this.requestFingerprint = requireHash(requestFingerprint, "request fingerprint");
        this.executionId = Objects.requireNonNull(executionId, "execution id is required");
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
        this.expiresAt = requireFutureExpiration(expiresAt, createdAt);
        status = CheckoutIdempotencyStatus.PROCESSING;
        updatedAt = createdAt;
    }

    public static CheckoutIdempotencyRecord start(
            String ownerSubject,
            CheckoutIdempotencyOperation operation,
            String idempotencyKeyHash,
            String requestFingerprint,
            Instant expiresAt,
            Instant createdAt) {
        return CheckoutIdempotencyRecord.builder()
                .id(UUID.randomUUID())
                .ownerSubject(ownerSubject)
                .operation(operation)
                .idempotencyKeyHash(idempotencyKeyHash)
                .requestFingerprint(requestFingerprint)
                .executionId(UUID.randomUUID())
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "current time is required"));
    }

    public boolean hasFingerprint(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public void restart(String fingerprint, Instant newExpiresAt, Instant occurredAt) {
        Instant restartTime = Objects.requireNonNull(occurredAt, "restart time is required");
        if (status == CheckoutIdempotencyStatus.PROCESSING || !isExpired(restartTime)) {
            throw new IllegalStateException("a processing or unexpired idempotency record cannot be restarted");
        }
        requestFingerprint = requireHash(fingerprint, "request fingerprint");
        executionId = UUID.randomUUID();
        status = CheckoutIdempotencyStatus.PROCESSING;
        resultOrderId = null;
        failureCode = null;
        expiresAt = requireFutureExpiration(newExpiresAt, restartTime);
        updatedAt = restartTime;
    }

    public void complete(UUID expectedExecutionId, UUID orderId, Instant occurredAt) {
        requireActiveExecution(expectedExecutionId);
        resultOrderId = Objects.requireNonNull(orderId, "result order id is required");
        failureCode = null;
        status = CheckoutIdempotencyStatus.COMPLETED;
        updatedAt = Objects.requireNonNull(occurredAt, "completion time is required");
    }

    public void fail(UUID expectedExecutionId, ErrorCode errorCode, Instant occurredAt) {
        requireActiveExecution(expectedExecutionId);
        failureCode =
                Objects.requireNonNull(errorCode, "failure code is required").name();
        resultOrderId = null;
        status = CheckoutIdempotencyStatus.FAILED;
        updatedAt = Objects.requireNonNull(occurredAt, "failure time is required");
    }

    private void requireActiveExecution(UUID expectedExecutionId) {
        if (status != CheckoutIdempotencyStatus.PROCESSING
                || !executionId.equals(Objects.requireNonNull(expectedExecutionId, "execution id is required"))) {
            throw new IllegalStateException("idempotency execution is not active");
        }
    }

    private static String requireHash(String value, String field) {
        String hash = Objects.requireNonNull(value, field + " is required");
        if (hash.length() != HASH_LENGTH) {
            throw new IllegalArgumentException(field + " must be a SHA-256 hex value");
        }
        return hash;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1 to " + maxLength + " characters");
        }
        return value;
    }

    private static Instant requireFutureExpiration(Instant expiration, Instant reference) {
        Instant value = Objects.requireNonNull(expiration, "expiration is required");
        if (!value.isAfter(reference)) {
            throw new IllegalArgumentException("idempotency expiration must be in the future");
        }
        return value;
    }
}
