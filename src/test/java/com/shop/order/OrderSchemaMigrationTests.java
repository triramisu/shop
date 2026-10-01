package com.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderSchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsOrderTablesWithVietnameseModulePrefixes() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name LIKE 'don_hang_%'
                 ORDER BY table_name
                """, String.class);

        assertThat(tables)
                .containsExactly(
                        "don_hang_don_dat_hang", "don_hang_gio_hang", "don_hang_muc_don_hang", "don_hang_muc_gio_hang");
    }

    @Test
    void protectsCartOwnershipItemsAndQuantityWithDatabaseConstraints() {
        List<String> cartConstraints = constraints("don_hang_gio_hang");
        List<String> itemConstraints = constraints("don_hang_muc_gio_hang");

        assertThat(cartConstraints).contains("uk_don_hang_gio_hang_owner");
        assertThat(itemConstraints)
                .contains(
                        "fk_don_hang_muc_gio_hang_cart",
                        "uk_don_hang_muc_gio_hang_variant",
                        "uk_don_hang_muc_gio_hang_sku",
                        "ck_don_hang_muc_gio_hang_quantity");
    }

    @Test
    void protectsTheOrderStatusAndProvidesLifecycleLookupIndexes() {
        assertThat(constraints("don_hang_don_dat_hang")).contains("ck_don_hang_don_dat_hang_status");
        assertThat(jdbcTemplate.queryForList("""
                SELECT LOWER(index_name)
                  FROM information_schema.indexes
                 WHERE table_schema = 'public'
                   AND table_name = 'don_hang_don_dat_hang'
                """, String.class))
                .contains("idx_don_hang_don_dat_hang_owner_created", "idx_don_hang_don_dat_hang_status_updated");
    }

    @Test
    void protectsOrderItemSnapshotAmountsAndInternalOwnership() {
        assertThat(constraints("don_hang_muc_don_hang"))
                .contains(
                        "fk_don_hang_muc_don_hang_order",
                        "uk_don_hang_muc_don_hang_line",
                        "ck_don_hang_muc_don_hang_line",
                        "ck_don_hang_muc_don_hang_quantity",
                        "ck_don_hang_muc_don_hang_unit_price",
                        "ck_don_hang_muc_don_hang_subtotal",
                        "ck_don_hang_muc_don_hang_discount",
                        "ck_don_hang_muc_don_hang_tax",
                        "ck_don_hang_muc_don_hang_total");
        assertThat(jdbcTemplate.queryForList("""
                SELECT LOWER(index_name)
                  FROM information_schema.indexes
                 WHERE table_schema = 'public'
                   AND table_name = 'don_hang_muc_don_hang'
                """, String.class)).contains("idx_don_hang_muc_don_hang_variant");
    }

    private List<String> constraints(String table) {
        return jdbcTemplate.queryForList("""
                SELECT LOWER(constraint_name)
                  FROM information_schema.table_constraints
                 WHERE table_schema = 'public'
                   AND table_name = ?
                """, String.class, table);
    }
}
