package com.shop.payment.internal.webhook.stripe;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.provider.stripe.StripeMoney;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.service.PaymentLifecycleEventPublisher;
import com.shop.payment.internal.webhook.entity.PaymentWebhookEvent;
import com.shop.payment.internal.webhook.entity.PaymentWebhookOutcome;
import com.shop.payment.internal.webhook.repository.PaymentWebhookEventRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
class StripeWebhookTransactionService {

    private static final String PROVIDER_CODE = "STRIPE";

    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentLifecycleEventPublisher lifecycleEventPublisher;
    private final String expectedApiVersion;
    private final boolean expectedLiveMode;

    @Transactional
    PaymentWebhookOutcome process(
            StripeWebhookEvent event, String payloadHash, Instant signatureTimestamp, Instant receivedAt) {
        PaymentWebhookEvent inboxEvent = PaymentWebhookEvent.receive(
                UUID.randomUUID(),
                PROVIDER_CODE,
                event.eventId(),
                event.eventType(),
                event.objectId(),
                payloadHash,
                signatureTimestamp,
                event.providerCreatedAt(),
                receivedAt);
        webhookEventRepository.saveAndFlush(inboxEvent);

        if (!event.isSupported()) {
            inboxEvent.complete(PaymentWebhookOutcome.IGNORED_UNSUPPORTED, null, receivedAt);
            webhookEventRepository.saveAndFlush(inboxEvent);
            return PaymentWebhookOutcome.IGNORED_UNSUPPORTED;
        }

        PaymentAttempt attempt = requireMatchingAttempt(event);
        PaymentStatus targetStatus = event.targetStatus();
        PaymentWebhookOutcome outcome;
        if (attempt.getStatus() == targetStatus) {
            outcome = PaymentWebhookOutcome.ALREADY_APPLIED;
        } else if (attempt.getStatus().isTerminal()) {
            outcome = PaymentWebhookOutcome.IGNORED_TERMINAL;
        } else {
            var statusChangedEvent =
                    attempt.transition(targetStatus, event.objectId(), null, event.failureCode(), receivedAt);
            paymentAttemptRepository.saveAndFlush(attempt);
            lifecycleEventPublisher.publish(statusChangedEvent);
            outcome = PaymentWebhookOutcome.APPLIED;
        }
        inboxEvent.complete(outcome, attempt.getId(), receivedAt);
        webhookEventRepository.saveAndFlush(inboxEvent);
        return outcome;
    }

    private PaymentAttempt requireMatchingAttempt(StripeWebhookEvent event) {
        StripeWebhookEvent.StripeCheckoutSessionData session = event.checkoutSession();
        PaymentAttempt attempt = paymentAttemptRepository
                .findByProviderCodeAndProviderReference(PROVIDER_CODE, event.objectId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_WEBHOOK_REFERENCE_INVALID));
        boolean matches = event.apiVersion().equals(expectedApiVersion)
                && event.liveMode() == expectedLiveMode
                && session.paymentAttemptId().equals(attempt.getId())
                && session.orderId().equals(attempt.getOrderId())
                && session.clientReferenceOrderId().equals(attempt.getOrderId())
                && session.amountInMinorUnit() == StripeMoney.toMinorUnit(attempt.getAmount(), attempt.getCurrency())
                && session.currency().equals(attempt.getCurrency());
        if (!matches) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_REFERENCE_INVALID);
        }
        return attempt;
    }
}
