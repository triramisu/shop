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
    void upgradesFromCatalogWithoutChangingExistingBusinessData() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:inventory-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .target(MigrationVersion.fromVersion("12"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        byte[] userId = HexFormat.of().parseHex("102132435465768798a9bacbdcedfe0f");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO xac_thuc_nguoi_dung
                    (id, username, email, password_hash, status, email_verified, version, created_at, updated_at)
                VALUES
                    (?, 'inventory-owner', 'inventory-owner@example.com', 'hash', 'ACTIVE', FALSE, 0, ?, ?)
                """, userId, Timestamp.from(now), Timestamp.from(now));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/h2")
                .load()
                .migrate();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT username FROM xac_thuc_nguoi_dung WHERE id = ?", String.class, userId))
                .isEqualTo("inventory-owner");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_name LIKE 'ton_kho_%'",
                        Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                        Integer.class))
                .isEqualTo(13);
    }
}
