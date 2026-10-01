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
    void upgradesFromV14WithoutChangingExistingInventoryData() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:order-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .target(MigrationVersion.fromVersion("14"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        byte[] stockItemId = HexFormat.of().parseHex("2031425364758697a8b9cadbecfd0e1f");
        byte[] variantId = HexFormat.of().parseHex("30415263748596a7b8c9daebfc0d1e2f");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO ton_kho_mat_hang
                    (id, product_variant_id, sku, location_code, on_hand, reserved_quantity,
                     version, created_at, updated_at)
                VALUES (?, ?, 'ORDER-UPGRADE-01', 'WAREHOUSE_UPGRADE', 12, 0, 0, ?, ?)
                """, stockItemId, variantId, Timestamp.from(now), Timestamp.from(now));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .load()
                .migrate();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT on_hand FROM ton_kho_mat_hang WHERE id = ?", Long.class, stockItemId))
                .isEqualTo(12L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM don_hang_gio_hang", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM don_hang_muc_gio_hang", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                        Integer.class))
                .isEqualTo(15);
    }
}
