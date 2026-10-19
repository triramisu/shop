package com.shop.order.internal.service;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static com.shop.order.support.OrderTestFixtures.pendingOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.identity.support.IdentityApiTestClient;
import com.shop.order.event.OrderStatus;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderPaymentApiTests {
    @Autowired
    MockMvc mvc;

    @Autowired
    CustomerOrderRepository orders;

    @Autowired
    PaymentAttemptRepository payments;

    @Test
    void ownerCanReadLatestPaymentButAnonymousForeignAndMissingCannotReadToken() throws Exception {
        String auth = token("payment-link-owner");
        String other = token("payment-link-other");
        var order = orders.saveAndFlush(pendingOrder("payment-link-owner", Instant.now()));
        var attempt = PaymentAttempt.start(
                UUID.randomUUID(),
                order.getId(),
                1,
                order.getGrandTotal(),
                order.getCurrency(),
                "STRIPE",
                Instant.now());
        URI action = URI.create("https://checkout.stripe.com/c/pay/cs_test_link");
        attempt.transition(PaymentStatus.REQUIRES_ACTION, "cs_test_link", action, null, Instant.now());
        payments.saveAndFlush(attempt);
        String path = "/api/orders/" + order.getId() + "/payment";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(1310));
        mvc.perform(get("/api/orders/" + UUID.randomUUID() + "/payment").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(1310));
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("result.paymentAttemptId")
                        .value(attempt.getId().toString()))
                .andExpect(jsonPath("result.actionUrl").value(action.toString()));
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
        orders.saveAndFlush(order);
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.actionUrl").isEmpty());
    }

    @Test
    void notYetInitiatedPaymentReturnsDocumented404WithoutCreatingOne() throws Exception {
        String auth = token("payment-wait-owner");
        var order = orders.saveAndFlush(pendingOrder("payment-wait-owner", Instant.now()));
        mvc.perform(get("/api/orders/" + order.getId() + "/payment").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(1407));
    }

    private String token(String username) throws Exception {
        var client = new IdentityApiTestClient(mvc);
        client.register(username);
        return bearer(client.authenticate(username).accessToken());
    }
}
