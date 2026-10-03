package com.shop.payment.internal.provider.stripe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.processing.PaymentAttemptSnapshot;
import com.shop.payment.processing.PaymentInitiationCommand;
import com.shop.payment.processing.PaymentInitiationOperations;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "app.payment.provider.type=stripe",
            "app.payment.stripe.success-url=https://shop.example.com/api/payments/checkout/return",
            "app.payment.stripe.cancel-url=https://shop.example.com/api/payments/checkout/cancel",
            "app.payment.stripe.allowed-return-hosts=shop.example.com"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@Import(StripeHostedCheckoutReturnIntegrationTests.StripeGatewayTestConfiguration.class)
class StripeHostedCheckoutReturnIntegrationTests {

    @DynamicPropertySource
    static void stripeCredentials(DynamicPropertyRegistry registry) {
        registry.add("app.payment.stripe.secret-key", StripeTestCredentials::apiKey);
        registry.add("app.payment.stripe.return-state-secret", StripeTestCredentials::stateSigningKey);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private StripeCheckoutStateSigner stateSigner;

    @Autowired
    private PaymentInitiationOperations paymentInitiationOperations;

    @Test
    void initiatesAndPersistsHostedCheckoutThroughTheRealAdapterBoundary() {
        UUID paymentAttemptId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        PaymentAttemptSnapshot result = paymentInitiationOperations.initiate(
                new PaymentInitiationCommand(paymentAttemptId, orderId, 1, new BigDecimal("10.00"), "USD"));

        org.assertj.core.api.Assertions.assertThat(result.status()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        org.assertj.core.api.Assertions.assertThat(result.providerCode()).isEqualTo("STRIPE");
        org.assertj.core.api.Assertions.assertThat(result.providerReference()).isEqualTo("cs_test_adapter_session");
        org.assertj.core.api.Assertions.assertThat(result.actionUrl())
                .isEqualTo(URI.create("https://checkout.stripe.com/c/pay/adapter-session"));
        org.assertj.core.api.Assertions.assertThat(paymentAttemptRepository
                        .findById(paymentAttemptId)
                        .orElseThrow()
                        .getActionUrl())
                .isEqualTo(result.actionUrl());
    }

    @Test
    void acceptsValidPublicReturnWithoutTrustingItAsPaymentSuccess() throws Exception {
        PaymentAttempt attempt = awaitingActionAttempt();
        paymentAttemptRepository.saveAndFlush(attempt);
        String state = stateSigner.issue(attempt.getId());

        mockMvc.perform(get("/api/payments/checkout/return")
                        .param("state", state)
                        .param("session_id", "cs_test_session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.paymentAttemptId")
                        .value(attempt.getId().toString()))
                .andExpect(jsonPath("$.result.status").value("REQUIRES_ACTION"))
                .andExpect(jsonPath("$.result.outcome").value("RETURNED"));

        mockMvc.perform(get("/api/payments/checkout/cancel").param("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REQUIRES_ACTION"))
                .andExpect(jsonPath("$.result.outcome").value("CANCELLED"));
    }

    @Test
    void rejectsInvalidPublicReturnUsingStandardApiErrorShape() throws Exception {
        mockMvc.perform(get("/api/payments/checkout/return")
                        .param("state", "tampered")
                        .param("session_id", "cs_test_session"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private PaymentAttempt awaitingActionAttempt() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        PaymentAttempt attempt = PaymentAttempt.start(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("10.00"), "USD", "STRIPE", now);
        attempt.transition(
                PaymentStatus.REQUIRES_ACTION,
                "cs_test_session",
                URI.create("https://checkout.stripe.com/c/pay/session"),
                null,
                now.plusSeconds(1));
        return attempt;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StripeGatewayTestConfiguration {

        @Bean
        @Primary
        StripeCheckoutGateway stripeCheckoutGatewayStub() {
            return request -> new StripeCheckoutSession(
                    "cs_test_adapter_session",
                    URI.create("https://checkout.stripe.com/c/pay/adapter-session"),
                    "open",
                    "unpaid",
                    request.orderId().toString(),
                    request.amountInMinorUnit(),
                    request.currency());
        }
    }
}
