package com.shop.payment.internal.webhook.stripe;

import com.shop.payment.event.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

record StripeWebhookEvent(
        String eventId,
        String eventType,
        Instant providerCreatedAt,
        String apiVersion,
        boolean liveMode,
        String objectId,
        StripeCheckoutSessionData checkoutSession) {

    boolean isSupported() {
        return checkoutSession != null;
    }

    PaymentStatus targetStatus() {
        if (!isSupported()) {
            throw new IllegalStateException("unsupported Stripe event has no payment target status");
        }
        return switch (eventType) {
            case "checkout.session.completed" ->
                "paid".equals(checkoutSession.paymentStatus()) ? PaymentStatus.SUCCEEDED : PaymentStatus.PENDING;
            case "checkout.session.async_payment_succeeded" -> PaymentStatus.SUCCEEDED;
            case "checkout.session.async_payment_failed" -> PaymentStatus.FAILED;
            case "checkout.session.expired" -> PaymentStatus.EXPIRED;
            default -> throw new IllegalStateException("unsupported Stripe event has no payment target status");
        };
    }

    String failureCode() {
        return targetStatus() == PaymentStatus.FAILED ? "STRIPE_ASYNC_PAYMENT_FAILED" : null;
    }

    record StripeCheckoutSessionData(
            UUID paymentAttemptId,
            UUID orderId,
            UUID clientReferenceOrderId,
            long amountInMinorUnit,
            String currency,
            String sessionStatus,
            String paymentStatus) {}
}
