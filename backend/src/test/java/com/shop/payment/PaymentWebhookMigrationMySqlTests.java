package com.shop.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PaymentWebhookMigrationMySqlTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_payment_webhook_migration_test")
            .withUsername("shop_webhook_migration")
            .withPassword("shop-payment-webhook-migration-test-password");

    @Test
    void upgradesV21DataAndCreatesTheWebhookInboxOnMySql() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/mysql")
                .target(MigrationVersion.fromVersion("21"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        UUID attemptId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-05T00:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO thanh_toan_lan_thu "
                        + "(id, order_id, attempt_number, amount, currency, provider_code, provider_reference, "
                        + "status, failure_code, action_url, completed_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, 1, ?, 'VND', 'STRIPE', 'cs_test_migration', 'REQUIRES_ACTION', NULL, "
                        + "'https://checkout.stripe.com/c/pay/cs_test_migration', NULL, 0, ?, ?)",
                toBytes(attemptId),
                toBytes(UUID.randomUUID()),
                new BigDecimal("199000.00"),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt.plusSeconds(1)));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/mysql")
                .load()
                .migrate();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM thanh_toan_lan_thu WHERE id = ?", Integer.class, toBytes(attemptId)))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = 'thanh_toan_su_kien_webhook'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.table_constraints "
                                + "WHERE table_schema = DATABASE() AND table_name = 'thanh_toan_su_kien_webhook' "
                                + "AND constraint_name = 'uk_thanh_toan_webhook_provider_event'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL",
                        Integer.class))
                .isEqualTo(24);
    }

    private byte[] toBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
