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
    void upgradesFromV15WithoutChangingExistingCartData() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:order-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .target(MigrationVersion.fromVersion("15"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        byte[] cartId = HexFormat.of().parseHex("2031425364758697a8b9cadbecfd0e1f");
        byte[] cartItemId = HexFormat.of().parseHex("405162738495a6b7c8d9eafb0c1d2e3f");
        byte[] variantId = HexFormat.of().parseHex("30415263748596a7b8c9daebfc0d1e2f");
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
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM don_hang_don_dat_hang", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                        Integer.class))
                .isEqualTo(16);
    }
}
