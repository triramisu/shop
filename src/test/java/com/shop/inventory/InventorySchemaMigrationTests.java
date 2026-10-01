package com.shop.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InventorySchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsEmptyInventoryTablesWithVietnameseModulePrefixes() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name LIKE 'ton_kho_%'
                 ORDER BY table_name
                """, String.class);

        assertThat(tables)
                .containsExactly(
                        "ton_kho_bien_dong", "ton_kho_giu_hang", "ton_kho_mat_hang", "ton_kho_yeu_cau_luy_dang");
        assertThat(rowCount("ton_kho_mat_hang")).isZero();
        assertThat(rowCount("ton_kho_bien_dong")).isZero();
        assertThat(rowCount("ton_kho_giu_hang")).isZero();
        assertThat(rowCount("ton_kho_yeu_cau_luy_dang")).isZero();
    }

    @Test
    void seedsInventoryPermissionsForAdminAndStaff() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code FROM xac_thuc_quyen_han WHERE code LIKE 'INVENTORY_%' ORDER BY code",
                        String.class))
                .containsExactly("INVENTORY_READ", "INVENTORY_WRITE");
        assertThat(jdbcTemplate.queryForList("""
                SELECT CONCAT(role_code, ':', permission_code)
                  FROM xac_thuc_vai_tro_quyen_han
                 WHERE permission_code LIKE 'INVENTORY_%'
                 ORDER BY role_code, permission_code
                """, String.class))
                .containsExactly(
                        "ADMIN:INVENTORY_READ",
                        "ADMIN:INVENTORY_WRITE",
                        "STAFF:INVENTORY_READ",
                        "STAFF:INVENTORY_WRITE");
    }

    @Test
    void protectsReservationReplayRecordsWithDatabaseConstraints() {
        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT LOWER(constraint_name)
                  FROM information_schema.table_constraints
                 WHERE table_schema = 'public'
                   AND table_name = 'ton_kho_yeu_cau_luy_dang'
                """, String.class);

        assertThat(constraints)
                .contains(
                        "uk_ton_kho_luy_dang_reservation_operation",
                        "fk_ton_kho_luy_dang_reservation",
                        "fk_ton_kho_luy_dang_mat_hang",
                        "ck_ton_kho_luy_dang_operation",
                        "ck_ton_kho_luy_dang_processing",
                        "ck_ton_kho_luy_dang_fingerprint",
                        "ck_ton_kho_luy_dang_balance");
    }

    private long rowCount(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }
}
