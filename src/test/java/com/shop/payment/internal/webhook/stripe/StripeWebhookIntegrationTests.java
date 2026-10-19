package com.shop.payment.internal.webhook.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.event.PaymentStatusChangedEvent;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.webhook.entity.PaymentWebhookOutcome;
import com.shop.payment.internal.webhook.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "app.payment.provider.type=stripe",
            "app.payment.stripe.secret-key=sk_test_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "app.payment.stripe.return-state-secret=test-state-key-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "app.payment.stripe.webhook-secrets=whsec_cccccccccccccccccccccccccccccccc",
            "app.payment.stripe.success-url=https://shop.example.com/api/payments/checkout/return",
            "app.payment.stripe.cancel-url=https://shop.example.com/api/payments/checkout/cancel",
            "app.payment.stripe.allowed-return-hosts=shop.example.com"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@RecordApplicationEvents
class StripeWebhookIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void validSignatureAppliesPaymentOnceAndDuplicateDeliveryIsAcknowledged() throws Exception {
        PreparedPayment payment = preparePayment("success");
        byte[] payload = completedEvent("evt_webhook_success", payment, 199000);
        Instant signedAt = Instant.now();

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", StripeWebhookTestSupport.signature(payload, signedAt))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.status").value("PROCESSED"));

        PaymentAttempt afterFirst =
                paymentAttemptRepository.findById(payment.attemptId()).orElseThrow();
        long versionAfterFirst = afterFirst.getVersion();
        assertThat(afterFirst.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(afterFirst.getActionUrl()).isNull();
        assertThat(applicationEvents.stream(PaymentStatusChangedEvent.class)
                        .filter(event -> event.paymentAttemptId().equals(payment.attemptId()))
                        .filter(event -> event.currentStatus() == PaymentStatus.SUCCEEDED))
                .hasSize(1);

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", StripeWebhookTestSupport.signature(payload, Instant.now()))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("DUPLICATE"));

        PaymentAttempt afterDuplicate =
                paymentAttemptRepository.findById(payment.attemptId()).orElseThrow();
        assertThat(afterDuplicate.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(afterDuplicate.getVersion()).isEqualTo(versionAfterFirst);
        assertThat(applicationEvents.stream(PaymentStatusChangedEvent.class)
                        .filter(event -> event.paymentAttemptId().equals(payment.attemptId()))
                        .filter(event -> event.currentStatus() == PaymentStatus.SUCCEEDED))
                .hasSize(1);
        assertThat(webhookEventRepository.findByProviderCodeAndProviderEventId("STRIPE", "evt_webhook_success"))
                .get()
                .satisfies(event -> {
                    assertThat(event.getOutcome()).isEqualTo(PaymentWebhookOutcome.APPLIED);
                    assertThat(event.getPaymentAttemptId()).isEqualTo(payment.attemptId());
                });
    }

    @Test
    void invalidAndExpiredSignaturesHaveNoDatabaseSideEffect() throws Exception {
        PreparedPayment payment = preparePayment("signature");
        byte[] payload = completedEvent("evt_webhook_signature", payment, 199000);

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", StripeWebhookTestSupport.signature("{}".getBytes(), Instant.now()))
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1401));
        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(
                                "Stripe-Signature",
                                StripeWebhookTestSupport.signature(
                                        payload, Instant.now().minusSeconds(301)))
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1401));

        assertThat(paymentAttemptRepository
                        .findById(payment.attemptId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(webhookEventRepository.findByProviderCodeAndProviderEventId("STRIPE", "evt_webhook_signature"))
                .isEmpty();
    }

    @Test
    void mismatchedServerAuthoritativeAmountRollsBackTheInboxClaim() throws Exception {
        PreparedPayment payment = preparePayment("amount");
        byte[] payload = completedEvent("evt_webhook_amount", payment, 1);

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", StripeWebhookTestSupport.signature(payload, Instant.now()))
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1403));

        assertThat(paymentAttemptRepository
                        .findById(payment.attemptId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(webhookEventRepository.findByProviderCodeAndProviderEventId("STRIPE", "evt_webhook_amount"))
                .isEmpty();
    }

    @Test
    void signedUnsupportedEventIsPersistedAsIgnoredWithoutTouchingPayment() throws Exception {
        byte[] payload = StripeWebhookTestSupport.unsupportedEvent("evt_webhook_ignored", Instant.now());

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", StripeWebhookTestSupport.signature(payload, Instant.now()))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("IGNORED"));

        assertThat(webhookEventRepository.findByProviderCodeAndProviderEventId("STRIPE", "evt_webhook_ignored"))
                .get()
                .satisfies(event -> {
                    assertThat(event.getOutcome()).isEqualTo(PaymentWebhookOutcome.IGNORED_UNSUPPORTED);
                    assertThat(event.getPaymentAttemptId()).isNull();
                });
    }

    @Test
    void apiVersionAndLiveModeMismatchCannotMutateThePayment() throws Exception {
        PreparedPayment payment = preparePayment("environment");
        byte[] valid = completedEvent("evt_webhook_environment", payment, 199000);
        byte[] wrongVersion = new String(valid, java.nio.charset.StandardCharsets.UTF_8)
                .replace(StripeWebhookTestSupport.API_VERSION, "2025-01-01.legacy")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] wrongMode = new String(valid, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"livemode\":false", "\"livemode\":true")
                .replace("evt_webhook_environment", "evt_webhook_live_mode")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        postSigned(wrongVersion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1403));
        postSigned(wrongMode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1403));

        assertThat(paymentAttemptRepository
                        .findById(payment.attemptId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(webhookEventRepository.findByProviderCodeAndProviderEventId("STRIPE", "evt_webhook_environment"))
                .isEmpty();
        assertThat(webhookEventRepository.findByProviderCodeAndProviderEventId("STRIPE", "evt_webhook_live_mode"))
                .isEmpty();
    }

    @Test
    void sameEventIdWithDifferentValidPayloadReturnsConflict() throws Exception {
        PreparedPayment payment = preparePayment("conflict");
        byte[] original = completedEvent("evt_webhook_conflict", payment, 199000);
        byte[] changed = StripeWebhookTestSupport.checkoutEvent(
                "evt_webhook_conflict",
                "checkout.session.completed",
                payment.sessionId(),
                payment.attemptId(),
                payment.orderId(),
                199000,
                "vnd",
                "complete",
                "unpaid",
                Instant.now());

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", StripeWebhookTestSupport.signature(original, Instant.now()))
                        .content(original))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", StripeWebhookTestSupport.signature(changed, Instant.now()))
                        .content(changed))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1404));

        assertThat(paymentAttemptRepository
                        .findById(payment.attemptId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void laterConflictingTerminalEventCannotDowngradeASuccessfulPayment() throws Exception {
        PreparedPayment payment = preparePayment("terminal");
        byte[] completed = completedEvent("evt_webhook_terminal_paid", payment, 199000);
        byte[] expired = StripeWebhookTestSupport.checkoutEvent(
                "evt_webhook_terminal_expired",
                "checkout.session.expired",
                payment.sessionId(),
                payment.attemptId(),
                payment.orderId(),
                199000,
                "vnd",
                "expired",
                "unpaid",
                Instant.now());

        postSigned(completed).andExpect(status().isOk());
        postSigned(expired)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("IGNORED"));

        assertThat(paymentAttemptRepository
                        .findById(payment.attemptId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(webhookEventRepository
                        .findByProviderCodeAndProviderEventId("STRIPE", "evt_webhook_terminal_expired")
                        .orElseThrow()
                        .getOutcome())
                .isEqualTo(PaymentWebhookOutcome.IGNORED_TERMINAL);
    }

    @Test
    void webhookIsPublicInSecurityButDocumentedWithoutBearerAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/payments/webhooks/stripe'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/payments/webhooks/stripe'].post.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/payments/webhooks/stripe'].post.parameters[0].name")
                        .value("Stripe-Signature"));
    }

    @Test
    void rejectsUnsupportedContentTypeAndOversizedPayloadWithStandardApiErrors() throws Exception {
        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1010));

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=" + "0".repeat(64))
                        .content(new byte[262_145]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1402));
    }

    private org.springframework.test.web.servlet.ResultActions postSigned(byte[] payload) throws Exception {
        return mockMvc.perform(post("/api/payments/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", StripeWebhookTestSupport.signature(payload, Instant.now()))
                .content(payload));
    }

    private PreparedPayment preparePayment(String suffix) {
        UUID attemptId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String sessionId = "cs_test_webhook_" + suffix;
        Instant createdAt = Instant.now().minusSeconds(10);
        PaymentAttempt attempt =
                PaymentAttempt.start(attemptId, orderId, 1, new BigDecimal("199000.00"), "VND", "STRIPE", createdAt);
        attempt.transition(
                PaymentStatus.REQUIRES_ACTION,
                sessionId,
                URI.create("https://checkout.stripe.com/c/pay/" + sessionId),
                null,
                createdAt.plusSeconds(1));
        paymentAttemptRepository.saveAndFlush(attempt);
        return new PreparedPayment(attemptId, orderId, sessionId);
    }

    private byte[] completedEvent(String eventId, PreparedPayment payment, long amount) {
        return StripeWebhookTestSupport.checkoutEvent(
                eventId,
                "checkout.session.completed",
                payment.sessionId(),
                payment.attemptId(),
                payment.orderId(),
                amount,
                "vnd",
                "complete",
                "paid",
                Instant.now());
    }

    private record PreparedPayment(UUID attemptId, UUID orderId, String sessionId) {}
}
