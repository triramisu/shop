package com.shop.payment.internal.refund.service;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.identity.support.IdentityApiTestClient;
import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.provider.fake.FakeRefundProvider;
import com.shop.payment.internal.refund.configuration.RefundProperties;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.provider.RefundProviderRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(
        properties = {
            "app.payment.refund.enabled=true",
            "spring.datasource.url=jdbc:h2:mem:refund_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefundApiTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PaymentAttemptRepository payments;

    @MockitoSpyBean
    FakeRefundProvider provider;

    @Autowired
    RefundProperties properties;

    @Autowired
    RefundService service;

    private String admin;
    private String user;
    private String staff;
    private String adminSubject;

    @BeforeAll
    void accounts() throws Exception {
        adminSubject = "refund-admin-" + UUID.randomUUID().toString().substring(0, 8);
        admin = token(adminSubject, "ADMIN");
        user = token("refund-user-" + UUID.randomUUID().toString().substring(0, 8), "USER");
        staff = token("refund-staff-" + UUID.randomUUID().toString().substring(0, 8), "STAFF");
    }

    @BeforeEach
    void enable() {
        properties.setEnabled(true);
        org.mockito.Mockito.doAnswer(invocation -> {
                    RefundProviderRequest request = invocation.getArgument(0);
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    assertThat(jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM thanh_toan_lan_hoan WHERE id=?",
                                    Integer.class,
                                    RefundMigrationFixtures.bytes(request.refundId())))
                            .isEqualTo(1);
                    return invocation.callRealMethod();
                })
                .when(provider)
                .refund(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enforcesRealJwtAndCapabilityOnReadAndWriteAndService() throws Exception {
        UUID paymentId = payment("FAKE", true);
        mvc.perform(post(path(paymentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path(paymentId) + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        for (String auth : List.of(user, staff)) {
            create(paymentId, "forbidden", "{\"currency\":\"USD\"}", auth).andExpect(status().isForbidden());
            mvc.perform(get(path(paymentId) + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isForbidden());
        }
        assertThat(provider.invocationCount(paymentId)).isZero();
        assertThatThrownBy(() -> service.get(paymentId, UUID.randomUUID())).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void partialThenFullIsServerCalculatedAndReplayDoesNotCallProviderOrChangePayment() throws Exception {
        UUID paymentId = payment("FAKE", true);
        var first = create(paymentId, "partial", "{\"amount\":40,\"currency\":\"usd\"}", admin)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.result.amount").value(40))
                .andExpect(jsonPath("$.result.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.actorSubject").doesNotExist())
                .andExpect(jsonPath("$.result.providerReference").doesNotExist())
                .andReturn();
        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.result.id");
        create(paymentId, "partial", "{\"amount\":40.00,\"currency\":\"USD\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(firstId));
        create(paymentId, "partial", "{\"amount\":41,\"currency\":\"USD\"}", admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1410));
        var full = create(paymentId, "full", "{\"currency\":\"USD\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.amount").value(60))
                .andReturn();
        String fullId = JsonPath.read(full.getResponse().getContentAsString(), "$.result.id");
        create(paymentId, "full", "{\"amount\":null,\"currency\":\"USD\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(fullId))
                .andExpect(jsonPath("$.result.amount").value(60));
        create(paymentId, "over", "{\"amount\":0.01,\"currency\":\"USD\"}", admin)
                .andExpect(status().isConflict());
        create(paymentId, "empty-full", "{\"currency\":\"USD\"}", admin).andExpect(status().isConflict());
        assertThat(provider.invocationCount(paymentId)).isEqualTo(2);
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM thanh_toan_lan_hoan WHERE actor_subject=?", Integer.class, adminSubject))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void pendingAndTimeoutHoldBudgetAndDoNotInvokeProviderOnHttpReplay() throws Exception {
        for (var mode : List.of(FakeRefundProvider.Mode.PENDING, FakeRefundProvider.Mode.TIMEOUT)) {
            UUID paymentId = payment("FAKE", true);
            provider.useMode(paymentId, mode);
            String expected = mode == FakeRefundProvider.Mode.TIMEOUT ? "UNKNOWN" : "PENDING";
            var response = create(paymentId, "uncertain", "{\"amount\":80,\"currency\":\"USD\"}", admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value(expected))
                    .andReturn();
            String id = JsonPath.read(response.getResponse().getContentAsString(), "$.result.id");
            provider.useMode(paymentId, FakeRefundProvider.Mode.SUCCESS);
            create(paymentId, "uncertain", "{\"amount\":80,\"currency\":\"USD\"}", admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.id").value(id))
                    .andExpect(jsonPath("$.result.status").value(expected));
            create(paymentId, "too-much", "{\"amount\":21,\"currency\":\"USD\"}", admin)
                    .andExpect(status().isConflict());
            assertThat(provider.invocationCount(paymentId)).isEqualTo(1);
        }
    }

    @Test
    void explicitFailureReleasesBudgetButSameKeyRemainsFailed() throws Exception {
        UUID paymentId = payment("FAKE", true);
        provider.useMode(paymentId, FakeRefundProvider.Mode.DECLINE);
        create(paymentId, "decline", "{\"currency\":\"USD\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("FAILED"))
                .andExpect(jsonPath("$.result.failureCode").value("FAKE_REFUND_DECLINED"));
        provider.useMode(paymentId, FakeRefundProvider.Mode.SUCCESS);
        create(paymentId, "decline", "{\"currency\":\"USD\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("FAILED"));
        create(paymentId, "new-approved-request", "{\"currency\":\"USD\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.amount").value(100));
        assertThat(provider.invocationCount(paymentId)).isEqualTo(2);
    }

    @Test
    void readsAreScopedAndAvailableWhenCreationIsDisabled() throws Exception {
        UUID paymentId = payment("FAKE", true);
        var result = create(paymentId, "lookup", "{\"amount\":10,\"currency\":\"USD\"}", admin)
                .andExpect(status().isOk())
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");
        properties.setEnabled(false);
        create(paymentId, "disabled", "{\"amount\":10,\"currency\":\"USD\"}", admin)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1412));
        mvc.perform(get(path(paymentId) + "/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(id));
        var wrongPayment = mvc.perform(get(path(UUID.randomUUID()) + "/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1411))
                .andReturn();
        var missing = mvc.perform(
                        get(path(paymentId) + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(wrongPayment.getResponse().getContentAsString())
                .isEqualTo(missing.getResponse().getContentAsString());
        assertThat(provider.invocationCount(paymentId)).isEqualTo(1);
    }

    @Test
    void rejectsMissingUncapturedAndUnsupportedPaymentsBeforeReserving() throws Exception {
        create(UUID.randomUUID(), "missing", "{\"currency\":\"USD\"}", admin).andExpect(status().isNotFound());
        UUID uncaptured = payment("FAKE", false);
        create(uncaptured, "unpaid", "{\"currency\":\"USD\"}", admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1408));
        UUID stripe = payment("STRIPE", true);
        create(stripe, "unsupported", "{\"currency\":\"USD\"}", admin)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1413));
        assertThat(provider.invocationCount(uncaptured)).isZero();
        assertThat(provider.invocationCount(stripe)).isZero();
    }

    @Test
    void validatesAmountsCurrencyKeysAndUuidsWithoutSideEffects() throws Exception {
        UUID paymentId = payment("FAKE", true);
        for (String body : List.of(
                "{}",
                "{\"currency\":\"EUR\"}",
                "{\"currency\":\"123\"}",
                "{\"amount\":0,\"currency\":\"USD\"}",
                "{\"amount\":-1,\"currency\":\"USD\"}",
                "{\"amount\":0.001,\"currency\":\"USD\"}",
                "{\"amount\":100000000000000000,\"currency\":\"USD\"}",
                "{\"amount\":\"bad\",\"currency\":\"USD\"}")) {
            create(paymentId, "invalid", body, admin).andExpect(status().isBadRequest());
        }
        for (String key : List.of("", "bad key", "!abc", "a".repeat(101))) {
            create(paymentId, key, "{\"currency\":\"USD\"}", admin).andExpect(status().isBadRequest());
        }
        mvc.perform(post(path(paymentId))
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1406));
        mvc.perform(post("/api/admin/payments/not-a-uuid/refunds")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
        assertThat(provider.invocationCount(paymentId)).isZero();
    }

    @Test
    void distinguishesKeyCaseEvenWithCaseInsensitiveMysqlStyleCollation() throws Exception {
        UUID paymentId = payment("FAKE", true);
        for (String key : List.of("Key", "key")) {
            create(paymentId, key, "{\"amount\":10,\"currency\":\"USD\"}", admin)
                    .andExpect(status().isOk());
        }
        assertThat(provider.invocationCount(paymentId)).isEqualTo(2);
    }

    private UUID payment(String providerCode, boolean captured) {
        return payments.saveAndFlush(RefundTestFixtures.payment(providerCode, captured))
                .getId();
    }

    private String token(String username, String role) throws Exception {
        var client = new IdentityApiTestClient(mvc);
        UUID id = client.register(username);
        jdbc.update("DELETE FROM xac_thuc_nguoi_dung_vai_tro WHERE user_id=?", id);
        jdbc.update("INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code) VALUES (?, ?)", id, role);
        return bearer(client.authenticate(username).accessToken());
    }

    private ResultActions create(UUID id, String key, String body, String token) throws Exception {
        return mvc.perform(post(path(id))
                .header(HttpHeaders.AUTHORIZATION, token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String path(UUID id) {
        return "/api/admin/payments/" + id + "/refunds";
    }
}
