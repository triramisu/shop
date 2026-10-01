package com.shop.order.internal.repository;

import static com.shop.order.support.OrderTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.order.internal.entity.CustomerOrder;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderSnapshotRepositoryIntegrationTests {

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanCommittedOrderData() {
        jdbcTemplate.update("DELETE FROM don_hang_don_dat_hang");
    }

    @Test
    void persistsAndReloadsTheCompleteImmutableSnapshot() {
        CustomerOrder saved = orderRepository.saveAndFlush(pendingOrder("snapshot-owner", Instant.now()));

        CustomerOrder reloaded = orderRepository.findDetailedById(saved.getId()).orElseThrow();

        assertThat(reloaded.getOwnerSubject()).isEqualTo("snapshot-owner");
        assertThat(reloaded.getCurrency()).isEqualTo("USD");
        assertThat(reloaded.getSubtotal()).isEqualByComparingTo("20.0000");
        assertThat(reloaded.getDiscount()).isEqualByComparingTo("2.0000");
        assertThat(reloaded.getTax()).isEqualByComparingTo("1.4400");
        assertThat(reloaded.getGrandTotal()).isEqualByComparingTo("19.4400");
        assertThat(reloaded.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isNotNull();
            assertThat(item.getProductVariantId()).hasToString("11111111-2222-3333-4444-555555555555");
            assertThat(item.getLineNumber()).isEqualTo(1);
            assertThat(item.getSku()).isEqualTo("TEST-SKU-01");
            assertThat(item.getProductName()).isEqualTo("Sản phẩm kiểm thử");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getUnitPrice()).isEqualByComparingTo("10.0000");
            assertThat(item.getSubtotal()).isEqualByComparingTo("20.0000");
            assertThat(item.getDiscount()).isEqualByComparingTo("2.0000");
            assertThat(item.getTax()).isEqualByComparingTo("1.4400");
            assertThat(item.getTotal()).isEqualByComparingTo("19.4400");
            assertThat(item.getCurrency()).isEqualTo("USD");
            assertThat(item.getCreatedAt()).isEqualTo(reloaded.getCreatedAt());
        });
        assertThatThrownBy(() -> reloaded.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
