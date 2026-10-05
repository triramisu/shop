package com.shop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class OrderPaymentMigrationMySqlTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_order_payment_migration_test")
            .withUsername("shop_order_payment_migration")
            .withPassword("shop-order-payment-migration-test-password");

    @Test
    void upgradesV22OrderDataAndEnforcesThePaymentInboxOnMySql() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/mysql")
                .target(MigrationVersion.fromVersion("22"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Fixture fixture = insertExistingOrder(jdbcTemplate);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/mysql")
                .load()
                .migrate();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM don_hang_dieu_phoi_ton_kho WHERE id = ?",
                        String.class,
                        toBytes(fixture.orchestrationId())))
                .isEqualTo("RESERVED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT payment_attempt_id FROM don_hang_dieu_phoi_ton_kho WHERE id = ?",
                        byte[].class,
                        toBytes(fixture.orchestrationId())))
                .isNull();

        UUID paymentAttemptId = UUID.randomUUID();
        Instant eventTime = fixture.createdAt().plusSeconds(30);
        assertThat(jdbcTemplate.update(
                        "UPDATE don_hang_dieu_phoi_ton_kho "
                                + "SET payment_attempt_id = ?, payment_attempt_number = 1, "
                                + "last_payment_status = 'PENDING', last_payment_event_at = ?, "
                                + "status = 'PAYMENT_PENDING' WHERE id = ?",
                        toBytes(paymentAttemptId),
                        Timestamp.from(eventTime),
                        toBytes(fixture.orchestrationId())))
                .isEqualTo(1);
        assertThat(jdbcTemplate.update(
                        "UPDATE don_hang_dong_giu_ton_kho SET status = 'CONFIRMED' WHERE reservation_id = ?",
                        toBytes(fixture.reservationId())))
                .isEqualTo(1);

        UUID eventId = UUID.randomUUID();
        insertPaymentEvent(jdbcTemplate, UUID.randomUUID(), eventId, paymentAttemptId, fixture.orderId(), eventTime);

        assertThatThrownBy(() -> insertPaymentEvent(
                        jdbcTemplate, UUID.randomUUID(), eventId, paymentAttemptId, fixture.orderId(), eventTime))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM don_hang_su_kien_thanh_toan WHERE event_id = ?",
                        Integer.class,
                        toBytes(eventId)))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL",
                        Integer.class))
                .isEqualTo(24);
    }

    private Fixture insertExistingOrder(JdbcTemplate jdbcTemplate) {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID productVariantId = UUID.randomUUID();
        UUID orchestrationId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-05T00:00:00Z");
        Timestamp createdTimestamp = Timestamp.from(createdAt);

        jdbcTemplate.update(
                "INSERT INTO don_hang_don_dat_hang "
                        + "(id, owner_subject, status, status_changed_at, version, created_at, updated_at) "
                        + "VALUES (?, 'migration-owner', 'PENDING', ?, 0, ?, ?)",
                toBytes(orderId),
                createdTimestamp,
                createdTimestamp,
                createdTimestamp);
        jdbcTemplate.update(
                "INSERT INTO don_hang_muc_don_hang "
                        + "(id, order_id, product_variant_id, line_number, sku, product_name, quantity, "
                        + "unit_price, subtotal_amount, discount_amount, tax_amount, total_amount, currency, created_at) "
                        + "VALUES (?, ?, ?, 1, 'MIGRATION-SKU-001', 'Sản phẩm migration', 1, ?, ?, 0, 0, ?, "
                        + "'VND', ?)",
                toBytes(orderItemId),
                toBytes(orderId),
                toBytes(productVariantId),
                new BigDecimal("199000.0000"),
                new BigDecimal("199000.0000"),
                new BigDecimal("199000.0000"),
                createdTimestamp);
        jdbcTemplate.update(
                "INSERT INTO don_hang_dieu_phoi_ton_kho "
                        + "(id, order_id, request_event_id, correlation_id, status, expires_at, failure_code, "
                        + "version, created_at, updated_at) VALUES (?, ?, ?, ?, 'RESERVED', ?, NULL, 0, ?, ?)",
                toBytes(orchestrationId),
                toBytes(orderId),
                toBytes(UUID.randomUUID()),
                toBytes(UUID.randomUUID()),
                Timestamp.from(createdAt.plusSeconds(3600)),
                createdTimestamp,
                createdTimestamp);
        jdbcTemplate.update(
                "INSERT INTO don_hang_dong_giu_ton_kho "
                        + "(reservation_id, orchestration_id, order_item_id, product_variant_id, line_number, sku, "
                        + "quantity, stock_item_id, status, failure_code, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 1, 'MIGRATION-SKU-001', 1, ?, 'RESERVED', NULL, ?, ?)",
                toBytes(reservationId),
                toBytes(orchestrationId),
                toBytes(orderItemId),
                toBytes(productVariantId),
                toBytes(UUID.randomUUID()),
                createdTimestamp,
                createdTimestamp);
        return new Fixture(orderId, orchestrationId, reservationId, createdAt);
    }

    private void insertPaymentEvent(
            JdbcTemplate jdbcTemplate, UUID id, UUID eventId, UUID paymentAttemptId, UUID orderId, Instant eventTime) {
        jdbcTemplate.update(
                "INSERT INTO don_hang_su_kien_thanh_toan "
                        + "(id, event_id, event_version, payload_hash, payment_attempt_id, order_id, "
                        + "previous_payment_status, payment_status, amount, currency, provider_code, "
                        + "provider_reference, payment_failure_code, outcome, processing_failure_code, "
                        + "event_occurred_at, received_at, processed_at, version) "
                        + "VALUES (?, ?, 1, ?, ?, ?, 'PENDING', 'SUCCEEDED', ?, 'VND', 'STRIPE', "
                        + "'cs_test_migration', NULL, 'COMPLETED', NULL, ?, ?, ?, 0)",
                toBytes(id),
                toBytes(eventId),
                "a".repeat(64),
                toBytes(paymentAttemptId),
                toBytes(orderId),
                new BigDecimal("199000.00"),
                Timestamp.from(eventTime),
                Timestamp.from(eventTime.plusSeconds(1)),
                Timestamp.from(eventTime.plusSeconds(2)));
    }

    private byte[] toBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private record Fixture(UUID orderId, UUID orchestrationId, UUID reservationId, Instant createdAt) {}
}
