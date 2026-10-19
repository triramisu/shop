package com.shop.order.internal.service;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static com.shop.order.support.OrderTestFixtures.itemDraft;
import static com.shop.order.support.OrderTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.identity.support.IdentityApiTestClient;
import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.repository.CustomerOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
@Transactional
class CustomerOrderHistoryApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    CustomerOrderRepository repository;

    @Autowired
    OrderSnapshotQueryService queryService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private String token(String username) throws Exception {
        var client = new IdentityApiTestClient(mvc);
        client.register(username);
        return bearer(client.authenticate(username).accessToken());
    }

    @Test
    void anonymousCannotReadHistoryOrDetail() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyPrincipalOrdersWithStablePagingAndSnapshotTotals() throws Exception {
        String auth = token("history-page-owner");
        var at = Instant.parse("2026-10-16T00:00:00Z");
        var low = pendingOrder("history-page-owner", at);
        var high = CustomerOrder.createPending("history-page-owner", List.of(itemDraft(), itemDraft()), at);
        ReflectionTestUtils.setField(low, "id", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ReflectionTestUtils.setField(high, "id", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        repository.saveAndFlush(low);
        repository.saveAndFlush(high);
        repository.saveAndFlush(pendingOrder("history-other-owner", at.plusSeconds(1)));

        mvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .param("size", "1")
                        .param("ownerSubject", "history-other-owner")
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.result.content.length()").value(1))
                .andExpect(jsonPath("$.result.content[0].id").value(high.getId().toString()))
                .andExpect(jsonPath("$.result.content[0].grandTotal").value(38.88))
                .andExpect(jsonPath("$.result.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.result.content[0].totalQuantity").value(4))
                .andExpect(jsonPath("$.result.content[0].ownerSubject").doesNotExist())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.sort").value("createdAt,DESC;id,DESC"));
        mvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].id").value(low.getId().toString()))
                .andExpect(jsonPath("$.result.last").value(true));
        mvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .param("page", "10000")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").isEmpty())
                .andExpect(jsonPath("$.result.totalElements").value(2));
    }

    @Test
    void detailIsOwnedCurrentAndReadOnlyAndForeignLooksLikeMissing() throws Exception {
        String ownerAuth = token("history-detail-owner");
        String otherAuth = token("history-detail-other");
        var order = repository.saveAndFlush(pendingOrder("history-detail-owner", Instant.now()));
        var before = jdbc.queryForMap(
                "SELECT status, version, updated_at FROM don_hang_don_dat_hang WHERE owner_subject = ?",
                "history-detail-owner");
        mvc.perform(get("/api/orders/" + order.getId()).header(HttpHeaders.AUTHORIZATION, ownerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.items[0].productName").value("Sản phẩm kiểm thử"))
                .andExpect(jsonPath("$.result.ownerSubject").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
        var foreign = mvc.perform(get("/api/orders/" + order.getId()).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1310))
                .andReturn();
        var missing = mvc.perform(get("/api/orders/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, otherAuth))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(foreign.getResponse().getContentAsString())
                .isEqualTo(missing.getResponse().getContentAsString());
        assertThat(jdbc.queryForMap(
                        "SELECT status, version, updated_at FROM don_hang_don_dat_hang WHERE owner_subject = ?",
                        "history-detail-owner"))
                .isEqualTo(before);
    }

    @Test
    void adminCannotBypassOwnershipAndOwnEmptyHistoryIsValid() throws Exception {
        var client = new IdentityApiTestClient(mvc);
        var adminId = client.register("history-own-admin");
        jdbc.update("DELETE FROM xac_thuc_nguoi_dung_vai_tro WHERE user_id = ?", adminId);
        jdbc.update("INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code) VALUES (?, 'ADMIN')", adminId);
        entityManager.clear();
        String adminAuth = bearer(client.authenticate("history-own-admin").accessToken());
        var order = repository.saveAndFlush(pendingOrder("history-someone-else", Instant.now()));
        mvc.perform(get("/api/orders/" + order.getId()).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1310));
        mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").isEmpty())
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    void rejectsInvalidQueryAndUuidWithoutUndocumentedServerErrors() throws Exception {
        String auth = token("history-validation");
        for (String page : List.of("-1", "10001", "not-a-number", "2147483648")) {
            mvc.perform(get("/api/orders")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .param("page", page))
                    .andExpect(status().isBadRequest());
        }
        for (String size : List.of("0", "-1", "51", "not-a-number")) {
            mvc.perform(get("/api/orders")
                            .header(HttpHeaders.AUTHORIZATION, auth)
                            .param("size", size))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/orders/not-a-uuid").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageUsesAtMostThreeQueriesRegardlessOfItemCount() {
        repository.saveAndFlush(
                CustomerOrder.createPending("history-query-count", List.of(itemDraft(), itemDraft()), Instant.now()));
        repository.saveAndFlush(
                pendingOrder("history-query-count", Instant.now().minusSeconds(1)));
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(queryService.getOwnedOrders("history-query-count", 0, 1).content())
                .hasSize(1);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }
}
