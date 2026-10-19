package com.shop.order.internal.service;

import static com.shop.order.support.OrderTestFixtures.itemDraft;
import static com.shop.order.support.OrderTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;

import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.repository.CustomerOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CustomerOrderHistoryMySqlTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("history_query_test")
            .withUsername("history_test")
            .withPassword("history-local-test-password");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
    }

    @Autowired
    CustomerOrderRepository repository;

    @Autowired
    OrderSnapshotQueryService service;

    @Test
    void mysqlBinaryUuidTieBreakAndBatchSnapshotStayWithinOwner() {
        var at = Instant.parse("2026-10-16T00:00:00Z");
        var low = pendingOrder("mysql-history-owner", at);
        var high = CustomerOrder.createPending("mysql-history-owner", List.of(itemDraft(), itemDraft()), at);
        ReflectionTestUtils.setField(low, "id", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ReflectionTestUtils.setField(high, "id", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        repository.saveAndFlush(low);
        repository.saveAndFlush(high);
        var foreign = repository.saveAndFlush(pendingOrder("mysql-history-other", at.plusSeconds(1)));
        var first = service.getOwnedOrders("MYSQL-HISTORY-OWNER", 0, 1);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.content()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(high.getId());
            assertThat(summary.totalQuantity()).isEqualTo(4);
            assertThat(summary.grandTotal()).isEqualByComparingTo("38.88");
        });
        assertThat(service.getOwnedOrders("mysql-history-owner", 1, 1).content())
                .singleElement()
                .satisfies(summary -> assertThat(summary.id()).isEqualTo(low.getId()));
        assertThat(repository.findOwnedOrdersWithItems("mysql-history-owner", List.of(high.getId(), foreign.getId())))
                .extracting(CustomerOrder::getId)
                .containsExactly(high.getId());
        assertThat(repository.findDetailedByIdAndOwnerSubject(foreign.getId(), "mysql-history-owner"))
                .isEmpty();
        assertThat(service.getOwnedOrders("mysql-history-owner", 2, 1).content())
                .isEmpty();
    }
}
