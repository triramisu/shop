package com.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OrderMigrationUpgradeTests {

    @Test
    void upgradesFromV18WithoutChangingExistingCartOrderSnapshotOrOrchestrationData() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:order-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .target(MigrationVersion.fromVersion("18"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        byte[] cartId = HexFormat.of().parseHex("2031425364758697a8b9cadbecfd0e1f");
        byte[] cartItemId = HexFormat.of().parseHex("405162738495a6b7c8d9eafb0c1d2e3f");
        byte[] variantId = HexFormat.of().parseHex("30415263748596a7b8c9daebfc0d1e2f");
        byte[] orderId = HexFormat.of().parseHex("5061728394a5b6c7d8e9fa0b1c2d3e4f");
        byte[] orderItemId = HexFormat.of().parseHex("60718293a4b5c6d7e8f90a1b2c3d4e5f");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO don_hang_gio_hang
                    (id, owner_subject, version, created_at, updated_at)
                VALUES (?, 'upgrade-owner', 0, ?, ?)
                """, cartId, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO don_hang_muc_gio_hang
                    (id, cart_id, product_variant_id, sku, quantity, created_at, updated_at)
                VALUES (?, ?, ?, 'ORDER-UPGRADE-01', 2, ?, ?)
                """, cartItemId, cartId, variantId, Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO don_hang_don_dat_hang
                    (id, owner_subject, status, status_changed_at, version, created_at, updated_at)
                VALUES (?, 'upgrade-owner', 'PENDING', ?, 0, ?, ?)
                """, orderId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO don_hang_muc_don_hang
                    (id, order_id, product_variant_id, line_number, sku, product_name, quantity,
                     unit_price, subtotal_amount, discount_amount, tax_amount, total_amount,
                     currency, created_at)
                VALUES (?, ?, ?, 1, 'ORDER-UPGRADE-01', 'Existing snapshot', 2,
                        10.0000, 20.0000, 2.0000, 1.4400, 19.4400, 'USD', ?)
                """, orderItemId, orderId, variantId, Timestamp.from(now));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .load()
                .migrate();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT owner_subject FROM don_hang_gio_hang WHERE id = ?", String.class, cartId))
                .isEqualTo("upgrade-owner");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT quantity FROM don_hang_muc_gio_hang WHERE id = ?", Integer.class, cartItemId))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT owner_subject FROM don_hang_don_dat_hang WHERE id = ?", String.class, orderId))
                .isEqualTo("upgrade-owner");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM don_hang_muc_don_hang", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM don_hang_dieu_phoi_ton_kho", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM don_hang_dong_giu_ton_kho", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM don_hang_yeu_cau_luy_dang", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                        Integer.class))
                .isEqualTo(24);
    }
}
