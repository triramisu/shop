package com.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CatalogMigrationUpgradeTests {

    @Test
    void upgradesAnExistingIdentitySchemaWithoutChangingExistingUsers() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:catalog-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("8"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        byte[] userId = HexFormat.of().parseHex("00112233445566778899aabbccddeeff");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO xac_thuc_nguoi_dung
                    (id, username, email, password_hash, status, email_verified, version, created_at, updated_at)
                VALUES
                    (?, 'catalog-owner', 'catalog-owner@example.com', 'hash', 'ACTIVE', FALSE, 0, ?, ?)
                """, userId, Timestamp.from(now), Timestamp.from(now));

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT username FROM xac_thuc_nguoi_dung WHERE id = ?", String.class, userId))
                .isEqualTo("catalog-owner");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_name LIKE 'san_pham_%'",
                        Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history " + "WHERE success = TRUE AND version IS NOT NULL",
                        Integer.class))
                .isEqualTo(9);
    }
}
