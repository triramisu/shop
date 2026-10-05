package com.shop.payment.internal.webhook.stripe;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class StripeWebhookTestSupport {

    static final String API_VERSION = "2026-09-30.endive";
    static final String WEBHOOK_SECRET = "whsec_" + "c".repeat(32);

    private StripeWebhookTestSupport() {}

    static byte[] checkoutEvent(
            String eventId,
            String eventType,
            String sessionId,
            UUID paymentAttemptId,
            UUID orderId,
            long amount,
            String currency,
            String sessionStatus,
            String paymentStatus,
            Instant createdAt) {
        return ("""
                {
                  "id":"%s",
                  "object":"event",
                  "api_version":"%s",
                  "created":%d,
                  "livemode":false,
                  "type":"%s",
                  "data":{"object":{
                    "id":"%s",
                    "object":"checkout.session",
                    "client_reference_id":"%s",
                    "amount_total":%d,
                    "currency":"%s",
                    "status":"%s",
                    "payment_status":"%s",
                    "metadata":{"payment_attempt_id":"%s","order_id":"%s"}
                  }}
                }
                """)
                .formatted(
                        eventId,
                        API_VERSION,
                        createdAt.getEpochSecond(),
                        eventType,
                        sessionId,
                        orderId,
                        amount,
                        currency,
                        sessionStatus,
                        paymentStatus,
                        paymentAttemptId,
                        orderId)
                .getBytes(StandardCharsets.UTF_8);
    }

    static byte[] unsupportedEvent(String eventId, Instant createdAt) {
        return ("""
                {
                  "id":"%s",
                  "object":"event",
                  "api_version":"%s",
                  "created":%d,
                  "livemode":false,
                  "type":"customer.created",
                  "data":{"object":{"id":"cus_test_001","object":"customer"}}
                }
                """).formatted(eventId, API_VERSION, createdAt.getEpochSecond()).getBytes(StandardCharsets.UTF_8);
    }

    static String signature(byte[] payload, Instant signedAt) {
        return signature(payload, signedAt, WEBHOOK_SECRET);
    }

    static String signature(byte[] payload, Instant signedAt, String secret) {
        long timestamp = signedAt.getEpochSecond();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(
                    (timestamp + "." + new String(payload, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
