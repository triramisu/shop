package com.shop.payment.internal.webhook.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.event.PaymentStatusChangedEvent;
import com.shop.payment.processing.PaymentInitiationCommand;
import com.shop.payment.processing.PaymentInitiationOperations;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class VnpayIpnIntegrationTests extends VnpayIntegrationSupport {
    @Autowired
    ApplicationEvents events;

    @Autowired
    PaymentInitiationOperations initiation;

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    void transactionFailureRollsBackAttemptAndInboxAndValidRetryCanCommit() {
        var attempt = seed();
        var fields = signed(callback(attempt));
        long count = inbox.count();
        ApplicationListener<ApplicationEvent> failingListener = event -> {
            if (event instanceof PayloadApplicationEvent<?> payload
                    && payload.getPayload() instanceof PaymentStatusChangedEvent changed
                    && changed.paymentAttemptId().equals(attempt.getId())) {
                throw new IllegalStateException("Simulated event publication failure");
            }
        };
        context.addApplicationListener(failingListener);
        try {
            assertThat(ipnService.receive(fields).code()).isEqualTo("99");
        } finally {
            context.removeApplicationListener(failingListener);
        }
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(inbox.count()).isEqualTo(count);
        assertThat(ipnService.receive(fields).code()).isEqualTo("00");
    }

    @Test
    void initiationIsIdempotentAndReturnsHostedHandoffWithoutStripe() {
        var command =
                new PaymentInitiationCommand(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("199000"), "VND");
        var first = initiation.initiate(command);
        var repeated = initiation.initiate(command);
        assertThat(first.status()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(repeated).isEqualTo(first);
        assertThat(first.actionUrl().toString()).contains("/api/payments/checkout/vnpay/start?attempt=" + first.id());
    }

    @Test
    void ipnUpdatesOnceAndReturnsExactProviderReceiptWithoutJwt() throws Exception {
        var attempt = seed();
        var params = signed(callback(attempt));
        mvc.perform(query(IPN, params))
                .andExpect(status().isOk())
                .andExpect(jsonPath("RspCode").value("00"))
                .andExpect(jsonPath("Message").value("Confirm Success"))
                .andExpect(jsonPath("result").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(query(IPN, params)).andExpect(jsonPath("RspCode").value("02"));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(events.stream(PaymentStatusChangedEvent.class)
                        .filter(e -> e.paymentAttemptId().equals(attempt.getId()))
                        .count())
                .isEqualTo(1);
        assertThat(inbox.findByProviderCodeAndProviderEventId(
                        "VNPAY", attempt.getProviderReference() + ":123456789:00:00"))
                .isPresent();
    }

    @Test
    void rejectsBadSignatureThenAcceptsValidRetryWithoutPoisonedInbox() throws Exception {
        var attempt = seed();
        var params = signed(callback(attempt));
        params.put("vnp_Amount", "100");
        mvc.perform(query(IPN, params)).andExpect(jsonPath("RspCode").value("97"));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        mvc.perform(query(IPN, signed(callback(attempt))))
                .andExpect(jsonPath("RspCode").value("00"));
    }

    @Test
    void rejectsAnotherMerchantEvenWithValidSignature() throws Exception {
        var attempt = seed();
        var params = callback(attempt);
        params.put("vnp_TmnCode", "OTHER123");
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("97"));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
    }

    @Test
    void wrongAmountReferenceOrOrderCannotMutatePayment() throws Exception {
        var attempt = seed();
        var params = callback(attempt);
        params.put("vnp_Amount", "19900001");
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("04"));
        params = callback(attempt);
        params.put("vnp_TxnRef", UUID.randomUUID().toString().replace("-", ""));
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("01"));
        params = callback(attempt);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + UUID.randomUUID());
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("01"));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"09", "10", "11", "12", "13", "24", "51", "65", "75", "79"})
    void definitiveFailureUsesExistingPaymentFailureEvent(String code) throws Exception {
        var attempt = seed();
        var params = callback(attempt);
        params.put("vnp_ResponseCode", code);
        params.put("vnp_TransactionStatus", "02");
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("00"));
        var stored = attempts.findById(attempt.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stored.getFailureCode()).isEqualTo("VNPAY_" + code + "_02");
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "07", "99", "88"})
    void uncertainOrFraudResultDoesNotDeclareFailureOrReleaseInventory(String code) throws Exception {
        var attempt = seed();
        var params = callback(attempt);
        params.put("vnp_ResponseCode", code);
        params.put("vnp_TransactionStatus", "01");
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("99"));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "04", "05", "06", "07", "09", "10", "20"})
    void cancellationWithInconsistentNonFailureTransactionStatusRemainsUncertain(String transactionStatus)
            throws Exception {
        var attempt = seed();
        var params = callback(attempt);
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_TransactionStatus", transactionStatus);
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("99"));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
    }

    @Test
    void browserReturnNeverConfirmsPaymentEvenWithValidSuccessSignature() throws Exception {
        var attempt = seed();
        mvc.perform(query("/api/payments/checkout/vnpay/return", signed(callback(attempt))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.signatureVerified").value(true))
                .andExpect(jsonPath("result.paymentConfirmedByBrowser").value(false));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_ACTION);
        mvc.perform(query("/api/payments/checkout/vnpay/return", callback(attempt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handoffRedirectIgnoresSpoofedForwardedAddressAndDoesNotExposeSecret() throws Exception {
        var attempt = seed();
        var result = mvc.perform(get("/api/payments/checkout/vnpay/start")
                        .param("attempt", attempt.getId().toString())
                        .param("token", hmac("shop-vnpay-handoff-v1:" + attempt.getId()))
                        .header("X-Forwarded-For", "1.2.3.4")
                        .with(r -> {
                            r.setRemoteAddr("203.0.113.9");
                            return r;
                        }))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        assertThat(result.getResponse().getHeader("Location"))
                .startsWith("https://sandbox.vnpayment.vn/")
                .contains("vnp_IpAddr=203.0.113.9")
                .doesNotContain("1.2.3.4", SECRET);
    }

    @Test
    void handoffRejectsInvalidTokenExpiredAttemptAndCompletedAttempt() throws Exception {
        var fresh = seed();
        mvc.perform(get("/api/payments/checkout/vnpay/start")
                        .param("attempt", fresh.getId().toString())
                        .param("token", "invalid"))
                .andExpect(status().isBadRequest());
        var old = seed(Instant.now().minusSeconds(601));
        mvc.perform(get("/api/payments/checkout/vnpay/start")
                        .param("attempt", old.getId().toString())
                        .param("token", hmac("shop-vnpay-handoff-v1:" + old.getId())))
                .andExpect(status().isBadRequest());
        mvc.perform(query(IPN, signed(callback(fresh))))
                .andExpect(jsonPath("RspCode").value("00"));
        mvc.perform(get("/api/payments/checkout/vnpay/start")
                        .param("attempt", fresh.getId().toString())
                        .param("token", hmac("shop-vnpay-handoff-v1:" + fresh.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lateSignedIpnStillRecordsFundsWithoutTrustingCheckoutExpiry() throws Exception {
        var old = seed(Instant.now().minusSeconds(1800));
        mvc.perform(query(IPN, signed(callback(old))))
                .andExpect(jsonPath("RspCode").value("00"));
        assertThat(attempts.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void terminalPaymentCannotBeDowngradedAndChangedReplayCannotBeAcknowledgedAsOriginal() throws Exception {
        var attempt = seed();
        var params = callback(attempt);
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("00"));
        params.put("vnp_BankCode", "OTHER");
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("99"));
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_TransactionStatus", "02");
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("02"));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void rejectsDuplicateParametersOversizeAndMalformedSignedFields() throws Exception {
        var attempt = seed();
        var params = signed(callback(attempt));
        mvc.perform(query(IPN, params).param("vnp_Amount", "100"))
                .andExpect(jsonPath("RspCode").value("99"));
        mvc.perform(get(IPN).queryParam("vnp_OrderInfo", "x".repeat(8193)))
                .andExpect(jsonPath("RspCode").value("99"));
        params = callback(attempt);
        params.put("vnp_CurrCode", "USD");
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("99"));
        params = callback(attempt);
        params.put("vnp_PayDate", "20261332121212");
        mvc.perform(query(IPN, signed(params))).andExpect(jsonPath("RspCode").value("99"));
        mvc.perform(post(IPN)).andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDescribesPublicIpnAndNoBearerRequirement() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/api/payments/webhooks/vnpay'].get").exists())
                .andExpect(jsonPath("$.paths['/api/payments/webhooks/vnpay'].get.security")
                        .doesNotExist());
    }

    private MockHttpServletRequestBuilder query(String path, Map<String, String> fields) {
        var request = get(path);
        fields.forEach(request::queryParam);
        return request;
    }
}
