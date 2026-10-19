package com.shop.payment.internal.webhook.vnpay;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.provider.vnpay.VnpayPaymentProvider;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.service.PaymentLifecycleEventPublisher;
import com.shop.payment.internal.webhook.entity.PaymentWebhookEvent;
import com.shop.payment.internal.webhook.entity.PaymentWebhookOutcome;
import com.shop.payment.internal.webhook.repository.PaymentWebhookEventRepository;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "vnpay")
class VnpayIpnTransactionService {
    private final PaymentAttemptRepository attempts;
    private final PaymentWebhookEventRepository inbox;
    private final PaymentLifecycleEventPublisher publisher;

    @Transactional(timeout = 5)
    public VnpayIpnReceipt process(VnpayIpnEvent event, String hash, Instant receivedAt) {
        var attempt = attempts.findForProviderUpdate(event.attemptId()).orElse(null);
        if (attempt == null
                || !VnpayPaymentProvider.CODE.equals(attempt.getProviderCode())
                || !event.reference().equals(attempt.getProviderReference())
                || !VnpayPaymentProvider.orderInfo(attempt.getOrderId()).equals(event.orderInfo())) {
            return VnpayIpnReceipt.of("01");
        }
        if (!"VND".equals(attempt.getCurrency())
                || !new BigInteger(VnpayPaymentProvider.amount(attempt.getAmount(), attempt.getCurrency()))
                        .equals(new BigInteger(event.amount()))) {
            return VnpayIpnReceipt.of("04");
        }
        var existing = inbox.findByProviderCodeAndProviderEventId(VnpayPaymentProvider.CODE, event.eventId());
        if (existing.isPresent()) {
            return VnpayIpnReceipt.of(existing.get().getPayloadHash().equals(hash) ? "02" : "99");
        }
        if (attempt.getStatus().isTerminal()) {
            return VnpayIpnReceipt.of("02");
        }
        if (!event.definitive()) {
            return VnpayIpnReceipt.of("99");
        }
        // Do not acknowledge an IPN racing with the initial action URL commit: let VNPay retry.
        if (attempt.getStatus() != PaymentStatus.REQUIRES_ACTION
                && attempt.getStatus() != PaymentStatus.PENDING
                && attempt.getStatus() != PaymentStatus.UNKNOWN) {
            return VnpayIpnReceipt.of("99");
        }
        var entry = PaymentWebhookEvent.receive(
                UUID.randomUUID(),
                VnpayPaymentProvider.CODE,
                event.eventId(),
                "payment.ipn",
                event.transactionNo(),
                hash,
                event.paidAt(),
                event.paidAt(),
                receivedAt);
        inbox.saveAndFlush(entry);
        var changed = attempt.transition(
                event.succeeded() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED,
                event.reference(),
                null,
                event.succeeded() ? null : "VNPAY_" + event.responseCode() + "_" + event.transactionStatus(),
                receivedAt.isBefore(attempt.getUpdatedAt()) ? attempt.getUpdatedAt() : receivedAt);
        attempts.saveAndFlush(attempt);
        publisher.publish(changed);
        entry.complete(PaymentWebhookOutcome.APPLIED, attempt.getId(), receivedAt);
        inbox.saveAndFlush(entry);
        return VnpayIpnReceipt.of("00");
    }
}
