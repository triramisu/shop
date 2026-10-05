package com.shop.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class InventoryMigrationUpgradeTests {

    @Test
    void upgradesFromInventoryV13WithoutChangingExistingReservationHistory() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:inventory-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .target(MigrationVersion.fromVersion("13"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        byte[] userId = HexFormat.of().parseHex("102132435465768798a9bacbdcedfe0f");
        byte[] stockItemId = HexFormat.of().parseHex("2031425364758697a8b9cadbecfd0e1f");
        byte[] variantId = HexFormat.of().parseHex("30415263748596a7b8c9daebfc0d1e2f");
        byte[] reservationId = HexFormat.of().parseHex("405162738495a6b7c8d9eafb0c1d2e3f");
        byte[] movementId = HexFormat.of().parseHex("5061728394a5b6c7d8e9fa0b1c2d3e4f");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO xac_thuc_nguoi_dung
                    (id, username, email, password_hash, status, email_verified, version, created_at, updated_at)
                VALUES
                    (?, 'inventory-owner', 'inventory-owner@example.com', 'hash', 'ACTIVE', FALSE, 0, ?, ?)
                """, userId, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO ton_kho_mat_hang
                    (id, product_variant_id, sku, location_code, on_hand, reserved_quantity,
                     version, created_at, updated_at)
                VALUES (?, ?, 'UPGRADE-SKU-01', 'WAREHOUSE_UPGRADE', 10, 2, 1, ?, ?)
                """, stockItemId, variantId, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update(
                """
                INSERT INTO ton_kho_giu_hang
                    (id, stock_item_id, quantity, status, expires_at, version, created_at, updated_at)
                VALUES (?, ?, 2, 'RESERVED', ?, 0, ?, ?)
                """,
                reservationId,
                stockItemId,
                Timestamp.from(now.plusSeconds(300)),
                Timestamp.from(now),
                Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO ton_kho_bien_dong
                    (id, stock_item_id, movement_type, on_hand_delta, reserved_delta,
                     on_hand_after, reserved_after, reason, reference_id, occurred_at)
                VALUES (?, ?, 'RESERVATION', 0, 2, 10, 2, 'Giữ tồn kho', ?, ?)
                """, movementId, stockItemId, "40516273-8495-a6b7-c8d9-eafb0c1d2e3f", Timestamp.from(now));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .load()
                .migrate();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT username FROM xac_thuc_nguoi_dung WHERE id = ?", String.class, userId))
                .isEqualTo("inventory-owner");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM ton_kho_giu_hang WHERE id = ?", String.class, reservationId))
                .isEqualTo("RESERVED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ton_kho_bien_dong WHERE stock_item_id = ?", Integer.class, stockItemId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ton_kho_yeu_cau_luy_dang", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_name LIKE 'ton_kho_%'",
                        Integer.class))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                        Integer.class))
                .isEqualTo(22);
    }
}
