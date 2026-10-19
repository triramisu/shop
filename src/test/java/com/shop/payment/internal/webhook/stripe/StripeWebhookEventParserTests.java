package com.shop.payment.internal.webhook.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.event.PaymentStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

class StripeWebhookEventParserTests {

    private final StripeWebhookEventParser parser = new StripeWebhookEventParser(new ObjectMapper());

    @Test
    void parsesSupportedCheckoutSessionWithoutExposingUnneededCustomerData() {
        UUID attemptId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        byte[] payload = StripeWebhookTestSupport.checkoutEvent(
                "evt_parser_001",
                "checkout.session.completed",
                "cs_test_parser_001",
                attemptId,
                orderId,
                199000,
                "vnd",
                "complete",
                "paid",
                Instant.parse("2026-10-05T00:59:00Z"));

        StripeWebhookEvent result = parser.parse(payload);

        assertThat(result.eventId()).isEqualTo("evt_parser_001");
        assertThat(result.targetStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.checkoutSession().paymentAttemptId()).isEqualTo(attemptId);
        assertThat(result.checkoutSession().orderId()).isEqualTo(orderId);
        assertThat(result.checkoutSession().amountInMinorUnit()).isEqualTo(199000);
        assertThat(result.checkoutSession().currency()).isEqualTo("VND");
    }

    @Test
    void keepsUnknownSignedEventAsIgnoredCandidate() {
        StripeWebhookEvent result = parser.parse(
                StripeWebhookTestSupport.unsupportedEvent("evt_parser_unknown", Instant.parse("2026-10-05T00:59:00Z")));

        assertThat(result.isSupported()).isFalse();
        assertThat(result.objectId()).isEqualTo("cus_test_001");
    }

    @ParameterizedTest
    @CsvSource({
        "checkout.session.completed,complete,unpaid,PENDING,-",
        "checkout.session.async_payment_succeeded,complete,paid,SUCCEEDED,-",
        "checkout.session.async_payment_failed,complete,unpaid,FAILED,STRIPE_ASYNC_PAYMENT_FAILED",
        "checkout.session.expired,expired,unpaid,EXPIRED,-"
    })
    void mapsEverySupportedEventToAnExplicitPaymentState(
            String eventType,
            String sessionStatus,
            String paymentStatus,
            PaymentStatus expectedStatus,
            String expectedFailureCode) {
        StripeWebhookEvent event = parser.parse(StripeWebhookTestSupport.checkoutEvent(
                "evt_parser_" + expectedStatus.name().toLowerCase(),
                eventType,
                "cs_test_parser_" + expectedStatus.name().toLowerCase(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                199000,
                "vnd",
                sessionStatus,
                paymentStatus,
                Instant.parse("2026-10-05T00:59:00Z")));

        assertThat(event.targetStatus()).isEqualTo(expectedStatus);
        assertThat(event.failureCode()).isEqualTo("-".equals(expectedFailureCode) ? null : expectedFailureCode);
    }

    @Test
    void rejectsMalformedJsonMissingMetadataAndImpossibleEventState() {
        assertInvalid("not-json".getBytes(StandardCharsets.UTF_8));

        UUID orderId = UUID.randomUUID();
        byte[] missingAttempt = StripeWebhookTestSupport.checkoutEvent(
                "evt_parser_invalid_1",
                "checkout.session.completed",
                "cs_test_parser_invalid_1",
                UUID.randomUUID(),
                orderId,
                199000,
                "vnd",
                "complete",
                "paid",
                Instant.parse("2026-10-05T00:59:00Z"));
        String withoutMetadata = new String(missingAttempt, StandardCharsets.UTF_8)
                .replaceFirst("\\\"payment_attempt_id\\\":\\\"[^\\\"]+\\\",", "");
        assertInvalid(withoutMetadata.getBytes(StandardCharsets.UTF_8));

        byte[] impossibleState = StripeWebhookTestSupport.checkoutEvent(
                "evt_parser_invalid_2",
                "checkout.session.async_payment_succeeded",
                "cs_test_parser_invalid_2",
                UUID.randomUUID(),
                orderId,
                199000,
                "vnd",
                "complete",
                "unpaid",
                Instant.parse("2026-10-05T00:59:00Z"));
        assertInvalid(impossibleState);
    }

    private void assertInvalid(byte[] payload) {
        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_PAYLOAD_INVALID));
    }
}
