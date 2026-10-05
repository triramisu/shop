package com.shop.payment.internal.webhook.stripe;

import com.shop.payment.internal.webhook.entity.PaymentWebhookEvent;
import com.shop.payment.internal.webhook.entity.PaymentWebhookOutcome;
import com.shop.payment.internal.webhook.repository.PaymentWebhookEventRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

@RequiredArgsConstructor
@Slf4j
class StripeWebhookService {

    private static final String PROVIDER_CODE = "STRIPE";

    private final StripeWebhookSignatureVerifier signatureVerifier;
    private final StripeWebhookEventParser eventParser;
    private final StripeWebhookTransactionService transactionService;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final StripeWebhookMetrics metrics;
    private final Clock clock;

    StripeWebhookReceipt receive(byte[] rawPayload, String signatureHeader) {
        metrics.record("received");
        VerifiedStripeSignature signature;
        try {
            signature = signatureVerifier.verify(rawPayload, signatureHeader);
        } catch (AppException exception) {
            metrics.record("signature_rejected");
            throw exception;
        }

        StripeWebhookEvent event;
        try {
            event = eventParser.parse(rawPayload);
        } catch (AppException exception) {
            metrics.record("payload_rejected");
            throw exception;
        }

        String payloadHash = sha256(rawPayload);
        Instant receivedAt = clock.instant();
        PaymentWebhookEvent existing = webhookEventRepository
                .findByProviderCodeAndProviderEventId(PROVIDER_CODE, event.eventId())
                .orElse(null);
        if (existing != null) {
            return duplicateReceipt(event, payloadHash, existing);
        }
        try {
            PaymentWebhookOutcome outcome =
                    transactionService.process(event, payloadHash, signature.signedAt(), receivedAt);
            if (outcome == PaymentWebhookOutcome.APPLIED || outcome == PaymentWebhookOutcome.ALREADY_APPLIED) {
                metrics.record("processed");
                log.info(
                        "Stripe webhook accepted: eventId={}, eventType={}, outcome={}",
                        event.eventId(),
                        event.eventType(),
                        outcome);
                return StripeWebhookReceipt.processed();
            }
            metrics.record("ignored");
            log.info(
                    "Stripe webhook ignored safely: eventId={}, eventType={}, outcome={}",
                    event.eventId(),
                    event.eventType(),
                    outcome);
            return StripeWebhookReceipt.ignored();
        } catch (DataIntegrityViolationException exception) {
            PaymentWebhookEvent concurrentlyCommitted = webhookEventRepository
                    .findByProviderCodeAndProviderEventId(PROVIDER_CODE, event.eventId())
                    .orElseThrow(() -> exception);
            return duplicateReceipt(event, payloadHash, concurrentlyCommitted);
        }
    }

    private StripeWebhookReceipt duplicateReceipt(
            StripeWebhookEvent event, String payloadHash, PaymentWebhookEvent existing) {
        if (!MessageDigest.isEqual(
                existing.getPayloadHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                payloadHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            metrics.record("event_conflict");
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_EVENT_CONFLICT);
        }
        metrics.record("duplicate");
        log.info("Duplicate Stripe webhook acknowledged: eventId={}, eventType={}", event.eventId(), event.eventType());
        return StripeWebhookReceipt.duplicate();
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
