package com.shop.payment.internal.refund.entity;

import com.shop.payment.internal.constant.PaymentTableNames;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.provider.RefundProviderResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = PaymentTableNames.REFUND_ATTEMPTS)
public class RefundAttempt {
    @Id
    @Column(columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_attempt_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID paymentAttemptId;

    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "provider_code", nullable = false, updatable = false, length = 50)
    private String providerCode;

    @Column(name = "provider_payment_reference", nullable = false, updatable = false, length = 150)
    private String providerPaymentReference;

    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    @Column(name = "key_hash", nullable = false, updatable = false, length = 64)
    private String keyHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String fingerprint;

    @Column(name = "actor_subject", nullable = false, updatable = false, length = 100)
    private String actorSubject;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RefundAttempt() {}

    public static RefundAttempt create(
            PaymentAttempt payment, BigDecimal amount, String keyHash, String fingerprint, String actor, Instant now) {
        Objects.requireNonNull(payment);
        if (payment.getStatus() != com.shop.payment.event.PaymentStatus.SUCCEEDED
                || amount == null
                || amount.signum() <= 0
                || amount.scale() > 2
                || amount.compareTo(payment.getAmount()) > 0
                || keyHash == null
                || !keyHash.matches("[a-f0-9]{64}")
                || fingerprint == null
                || !fingerprint.matches("[a-f0-9]{64}")
                || actor == null
                || actor.isBlank()
                || actor.length() > 100) {
            throw new IllegalArgumentException("Dữ liệu khoản hoàn không hợp lệ");
        }
        RefundAttempt attempt = new RefundAttempt();
        attempt.id = UUID.randomUUID();
        attempt.paymentAttemptId = payment.getId();
        attempt.orderId = payment.getOrderId();
        attempt.providerCode = payment.getProviderCode();
        attempt.providerPaymentReference = payment.getProviderReference();
        attempt.amount = amount;
        attempt.currency = payment.getCurrency();
        attempt.keyHash = keyHash;
        attempt.fingerprint = fingerprint;
        attempt.actorSubject = actor;
        attempt.status = RefundStatus.CREATED;
        attempt.createdAt = Objects.requireNonNull(now);
        attempt.updatedAt = now;
        return attempt;
    }

    public boolean apply(RefundProviderResult result, Instant now) {
        Objects.requireNonNull(result);
        if (status.isTerminal()) {
            return false;
        }
        if (now == null || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Thời điểm hoàn tiền không được lùi");
        }
        if (providerReference != null
                && result.providerReference() != null
                && !providerReference.equals(result.providerReference())) {
            throw new IllegalArgumentException("Không được đổi mã hoàn tiền của provider");
        }
        if (result.providerReference() != null) {
            providerReference = result.providerReference();
        }
        status = RefundStatus.valueOf(result.status().name());
        failureCode = result.failureCode();
        updatedAt = now;
        return true;
    }
}
