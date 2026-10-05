package com.shop.payment.internal.webhook.entity;

import com.shop.payment.internal.constant.PaymentTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = PaymentTableNames.WEBHOOK_EVENTS)
public class PaymentWebhookEvent {

    private static final int MAX_PROVIDER_CODE_LENGTH = 50;
    private static final int MAX_EVENT_ID_LENGTH = 150;
    private static final int MAX_EVENT_TYPE_LENGTH = 100;
    private static final int MAX_OBJECT_ID_LENGTH = 150;

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "provider_code", nullable = false, updatable = false, length = MAX_PROVIDER_CODE_LENGTH)
    private String providerCode;

    @Column(name = "provider_event_id", nullable = false, updatable = false, length = MAX_EVENT_ID_LENGTH)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = MAX_EVENT_TYPE_LENGTH)
    private String eventType;

    @Column(name = "provider_object_id", nullable = false, updatable = false, length = MAX_OBJECT_ID_LENGTH)
    private String providerObjectId;

    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(name = "signature_timestamp", nullable = false, updatable = false)
    private Instant signatureTimestamp;

    @Column(name = "provider_created_at", nullable = false, updatable = false)
    private Instant providerCreatedAt;

    @Column(name = "payment_attempt_id", columnDefinition = "BINARY(16)")
    private UUID paymentAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentWebhookOutcome outcome;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected PaymentWebhookEvent() {}

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentWebhookEvent(
            UUID id,
            String providerCode,
            String providerEventId,
            String eventType,
            String providerObjectId,
            String payloadHash,
            Instant signatureTimestamp,
            Instant providerCreatedAt,
            Instant receivedAt) {
        this.id = Objects.requireNonNull(id, "webhook inbox id is required");
        this.providerCode = normalizeProviderCode(providerCode);
        this.providerEventId = normalize(providerEventId, MAX_EVENT_ID_LENGTH, "provider event id");
        this.eventType = normalize(eventType, MAX_EVENT_TYPE_LENGTH, "webhook event type");
        this.providerObjectId = normalize(providerObjectId, MAX_OBJECT_ID_LENGTH, "provider object id");
        this.payloadHash = requirePayloadHash(payloadHash);
        this.signatureTimestamp = Objects.requireNonNull(signatureTimestamp, "signature timestamp is required");
        this.providerCreatedAt = Objects.requireNonNull(providerCreatedAt, "provider creation time is required");
        this.receivedAt = Objects.requireNonNull(receivedAt, "webhook receipt time is required");
        processedAt = receivedAt;
        outcome = PaymentWebhookOutcome.RECEIVED;
    }

    public static PaymentWebhookEvent receive(
            UUID id,
            String providerCode,
            String providerEventId,
            String eventType,
            String providerObjectId,
            String payloadHash,
            Instant signatureTimestamp,
            Instant providerCreatedAt,
            Instant receivedAt) {
        return PaymentWebhookEvent.builder()
                .id(id)
                .providerCode(providerCode)
                .providerEventId(providerEventId)
                .eventType(eventType)
                .providerObjectId(providerObjectId)
                .payloadHash(payloadHash)
                .signatureTimestamp(signatureTimestamp)
                .providerCreatedAt(providerCreatedAt)
                .receivedAt(receivedAt)
                .build();
    }

    public void complete(PaymentWebhookOutcome result, UUID attemptId, Instant completionTime) {
        if (outcome != PaymentWebhookOutcome.RECEIVED) {
            throw new IllegalStateException("webhook event has already been completed");
        }
        PaymentWebhookOutcome resolved = Objects.requireNonNull(result, "webhook outcome is required");
        if (resolved == PaymentWebhookOutcome.RECEIVED) {
            throw new IllegalArgumentException("received is not a completed webhook outcome");
        }
        if (resolved == PaymentWebhookOutcome.IGNORED_UNSUPPORTED && attemptId != null) {
            throw new IllegalArgumentException("unsupported webhook event must not reference a payment attempt");
        }
        if (resolved != PaymentWebhookOutcome.IGNORED_UNSUPPORTED && attemptId == null) {
            throw new IllegalArgumentException("processed webhook event must reference a payment attempt");
        }
        Instant completed = Objects.requireNonNull(completionTime, "webhook completion time is required");
        if (completed.isBefore(receivedAt)) {
            throw new IllegalArgumentException("webhook completion time cannot move backwards");
        }
        paymentAttemptId = attemptId;
        outcome = resolved;
        processedAt = completed;
    }

    private static String normalizeProviderCode(String value) {
        String normalized =
                normalize(value, MAX_PROVIDER_CODE_LENGTH, "provider code").toUpperCase(Locale.ROOT);
        if (normalized.length() < 2 || !normalized.matches("[A-Z0-9][A-Z0-9_-]*")) {
            throw new IllegalArgumentException("provider code is invalid");
        }
        return normalized;
    }

    private static String requirePayloadHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("webhook payload hash is invalid");
        }
        return value;
    }

    private static String normalize(String value, int maximumLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalized;
    }
}
