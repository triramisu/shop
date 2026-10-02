package com.shop.payment.internal.entity;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.event.PaymentStatusChangedEvent;
import com.shop.payment.internal.constant.PaymentTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = PaymentTableNames.PAYMENT_ATTEMPTS)
public class PaymentAttempt {

    private static final int MAX_PROVIDER_CODE_LENGTH = 50;
    private static final int MAX_PROVIDER_REFERENCE_LENGTH = 150;
    private static final int MAX_FAILURE_CODE_LENGTH = 100;

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "provider_code", nullable = false, updatable = false, length = MAX_PROVIDER_CODE_LENGTH)
    private String providerCode;

    @Column(name = "provider_reference", length = MAX_PROVIDER_REFERENCE_LENGTH)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "failure_code", length = MAX_FAILURE_CODE_LENGTH)
    private String failureCode;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentAttempt() {}

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentAttempt(
            UUID id,
            UUID orderId,
            int attemptNumber,
            BigDecimal amount,
            String currency,
            String providerCode,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "payment attempt id is required");
        this.orderId = Objects.requireNonNull(orderId, "order id is required");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("payment attempt number must be positive");
        }
        this.attemptNumber = attemptNumber;
        this.amount = normalizeAmount(amount);
        this.currency = normalizeCurrency(currency);
        this.providerCode = normalizeProviderCode(providerCode);
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
        updatedAt = createdAt;
        status = PaymentStatus.CREATED;
    }

    public static PaymentAttempt start(
            UUID id,
            UUID orderId,
            int attemptNumber,
            BigDecimal amount,
            String currency,
            String providerCode,
            Instant createdAt) {
        return PaymentAttempt.builder()
                .id(id)
                .orderId(orderId)
                .attemptNumber(attemptNumber)
                .amount(amount)
                .currency(currency)
                .providerCode(providerCode)
                .createdAt(createdAt)
                .build();
    }

    public PaymentStatusChangedEvent transition(
            PaymentStatus targetStatus, String newProviderReference, String newFailureCode, Instant occurredAt) {
        PaymentStatus target = Objects.requireNonNull(targetStatus, "target payment status is required");
        PaymentStateMachine.requireTransition(status, target);
        Instant transitionTime = Objects.requireNonNull(occurredAt, "transition time is required");
        if (transitionTime.isBefore(updatedAt)) {
            throw new IllegalArgumentException("payment transition time cannot move backwards");
        }

        String resolvedProviderReference = resolveProviderReference(target, newProviderReference);
        String resolvedFailureCode = resolveFailureCode(target, newFailureCode);
        PaymentStatus previousStatus = status;
        status = target;
        providerReference = resolvedProviderReference;
        failureCode = resolvedFailureCode;
        updatedAt = transitionTime;
        completedAt = target.isTerminal() ? transitionTime : null;

        return new PaymentStatusChangedEvent(
                UUID.randomUUID(),
                id,
                orderId,
                previousStatus,
                target,
                amount,
                currency,
                providerCode,
                providerReference,
                failureCode,
                transitionTime);
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
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    private String resolveProviderReference(PaymentStatus target, String value) {
        String candidate = value == null ? null : normalizeProviderReference(value);
        if (providerReference != null && candidate != null && !providerReference.equals(candidate)) {
            throw new IllegalArgumentException("provider reference cannot be changed after it is assigned");
        }
        String resolved = candidate == null ? providerReference : candidate;
        if (requiresProviderReference(target) && resolved == null) {
            throw new IllegalArgumentException("provider reference is required for payment status " + target);
        }
        return resolved;
    }

    private String resolveFailureCode(PaymentStatus target, String value) {
        if (target == PaymentStatus.FAILED) {
            return normalizeFailureCode(value);
        }
        if (value != null && !value.isBlank()) {
            throw new IllegalArgumentException("failure code is only valid for a failed payment");
        }
        return null;
    }

    private static boolean requiresProviderReference(PaymentStatus target) {
        return target == PaymentStatus.PENDING
                || target == PaymentStatus.REQUIRES_ACTION
                || target == PaymentStatus.SUCCEEDED;
    }

    private static BigDecimal normalizeAmount(BigDecimal value) {
        BigDecimal normalized =
                Objects.requireNonNull(value, "payment amount is required").setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.signum() <= 0 || normalized.precision() > 19) {
            throw new IllegalArgumentException("payment amount is invalid");
        }
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("payment currency is required");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("payment currency is invalid");
        }
        return normalized;
    }

    private static String normalizeProviderCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider code is required");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() < 2
                || normalized.length() > MAX_PROVIDER_CODE_LENGTH
                || !normalized.matches("[A-Z0-9][A-Z0-9_-]*")) {
            throw new IllegalArgumentException("provider code is invalid");
        }
        return normalized;
    }

    private static String normalizeProviderReference(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("provider reference must not be blank");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_PROVIDER_REFERENCE_LENGTH) {
            throw new IllegalArgumentException("provider reference is too long");
        }
        return normalized;
    }

    private static String normalizeFailureCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("failure code is required for a failed payment");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_FAILURE_CODE_LENGTH || !normalized.matches("[A-Z0-9][A-Z0-9_.-]*")) {
            throw new IllegalArgumentException("payment failure code is invalid");
        }
        return normalized;
    }
}
