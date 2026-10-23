package com.shop.order.internal.service;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static com.shop.identity.support.IdentityApiTestClient.tokenJson;
import static com.shop.order.support.OrderTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.identity.support.IdentityApiTestClient;
import com.shop.order.internal.entity.Cart;
import com.shop.order.internal.repository.CartRepository;
import com.shop.order.internal.repository.CustomerOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoUserAuthorizationMatrixTests {

    private record Probe(String path, int anonymous, int user) {}

    private static final List<Probe> PROBES = List.of(
            new Probe("/api/catalog/categories", 200, 200),
            new Probe("/api/catalog/products?page=0&size=1", 200, 200),
            new Probe("/api/auth/my-info", 401, 200),
            new Probe("/api/cart", 401, 200),
            new Probe("/api/orders?page=0&size=1", 401, 200),
            new Probe("/api/admin/catalog/products?page=0&size=1", 401, 403),
            new Probe("/api/admin/inventory/stock-items?page=0&size=1", 401, 403),
            new Probe("/api/system-administration/users?page=0&size=1", 401, 403));

    @Autowired
    MockMvc mvc;

    @Autowired
    CartRepository carts;

    @Autowired
    CustomerOrderRepository orders;

    static Stream<Probe> probes() {
        return PROBES.stream();
    }

    @ParameterizedTest
    @MethodSource("probes")
    void anonymousAndOrdinaryUserHaveTheDocumentedAccess(Probe probe) throws Exception {
        mvc.perform(get(probe.path()))
                .andExpect(status().is(probe.anonymous()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        var tokens = user("matrix");
        mvc.perform(get("/api/auth/my-info").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.roles.length()").value(1))
                .andExpect(jsonPath("$.result.roles[0]").value("USER"));
        mvc.perform(get(probe.path()).header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().is(probe.user()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @MethodSource("probes")
    void invalidOrRefreshBearerCannotBeUsedAsAnAccessToken(Probe probe) throws Exception {
        var tokens = user("wrong-purpose");
        for (String rejected : List.of("invalid.jwt.signature", tokens.refreshToken())) {
            mvc.perform(get(probe.path()).header(HttpHeaders.AUTHORIZATION, bearer(rejected)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(1006));
        }
    }

    @Test
    void logoutRevokesAccessAcrossTheWholeMatrix() throws Exception {
        var tokens = user("revoke");
        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(tokens.refreshToken())))
                .andExpect(status().isOk());
        for (Probe probe : PROBES) {
            mvc.perform(get(probe.path()).header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(1006));
        }
    }

    @Test
    void foreignOrderAndPaymentAreIndistinguishableFromMissingAndHistoryStaysPrivate() throws Exception {
        String owner = username("order-owner");
        String other = username("order-other");
        var client = new IdentityApiTestClient(mvc);
        client.register(owner);
        client.register(other);
        String ownerAuth = bearer(client.authenticate(owner).accessToken());
        String otherAuth = bearer(client.authenticate(other).accessToken());
        var order = orders.saveAndFlush(pendingOrder(owner, Instant.now()));

        mvc.perform(get("/api/orders/" + order.getId()).header(HttpHeaders.AUTHORIZATION, ownerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(order.getId().toString()));
        for (String suffix : List.of("", "/payment")) {
            var foreign = mvc.perform(
                            get("/api/orders/" + order.getId() + suffix).header(HttpHeaders.AUTHORIZATION, otherAuth))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(1310))
                    .andReturn();
            var missing = mvc.perform(get("/api/orders/" + UUID.randomUUID() + suffix)
                            .header(HttpHeaders.AUTHORIZATION, otherAuth))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(1310))
                    .andReturn();
            assertThat(foreign.getResponse().getContentAsString())
                    .isEqualTo(missing.getResponse().getContentAsString());
        }
        mvc.perform(get("/api/orders").param("ownerSubject", owner).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").isEmpty())
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    void foreignCartUpdateAndDeleteCannotChangeTheOwnersItem() throws Exception {
        String owner = username("cart-owner");
        String other = username("cart-other");
        var client = new IdentityApiTestClient(mvc);
        client.register(owner);
        client.register(other);
        String ownerAuth = bearer(client.authenticate(owner).accessToken());
        String otherAuth = bearer(client.authenticate(other).accessToken());
        var cart = Cart.create(owner);
        var item = cart.addOrIncrement(UUID.randomUUID(), "DEMO-OWNERSHIP-SKU", 2);
        carts.saveAndFlush(cart);
        carts.saveAndFlush(Cart.create(other));

        var foreignUpdate = mvc.perform(put("/api/cart/items/" + item.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1301))
                .andReturn();
        var missingUpdate = mvc.perform(put("/api/cart/items/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1301))
                .andReturn();
        assertThat(foreignUpdate.getResponse().getContentAsString())
                .isEqualTo(missingUpdate.getResponse().getContentAsString());
        var foreignDelete = mvc.perform(
                        delete("/api/cart/items/" + item.getId()).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1301))
                .andReturn();
        var missingDelete = mvc.perform(
                        delete("/api/cart/items/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1301))
                .andReturn();
        assertThat(foreignDelete.getResponse().getContentAsString())
                .isEqualTo(missingDelete.getResponse().getContentAsString());
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, ownerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalQuantity").value(2))
                .andExpect(jsonPath("$.result.items[0].id").value(item.getId().toString()));
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isEmpty());
    }

    private IdentityApiTestClient.TokenPair user(String label) throws Exception {
        var client = new IdentityApiTestClient(mvc);
        String name = username(label);
        client.register(name);
        return client.authenticate(name);
    }

    private String username(String label) {
        return "demo-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
