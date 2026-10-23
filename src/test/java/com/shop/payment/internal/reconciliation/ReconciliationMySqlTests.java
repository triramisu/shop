package com.shop.payment.internal.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class ReconciliationMySqlTests extends ReconciliationIntegrationSupport {
    private static final UUID OLD = UUID.fromString("00000000-0000-0000-0000-000000000026");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.46")
            .withDatabaseName("payment_reconciliation_test")
            .withUsername("reconcile_test")
            .withPassword("reconcile-test-only-password");

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration", "classpath:db/mysql")
                .target("25")
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(source);
        Instant time = Instant.parse("2026-10-20T00:00:00Z");
        jdbc.update(
                """
                INSERT INTO thanh_toan_lan_thu
                  (id,order_id,attempt_number,amount,currency,provider_code,provider_reference,status,completed_at,version,created_at,updated_at)
                VALUES (?,?,1,199000,'VND','VNPAY','upgrade-captured','SUCCEEDED',?,0,?,?)
                """,
                ReconciliationRepository.bytes(OLD),
                ReconciliationRepository.bytes(UUID.randomUUID()),
                ReconciliationRepository.time(time),
                ReconciliationRepository.time(time),
                ReconciliationRepository.time(time));
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void upgradesV25WithoutChangingCapturedPaymentAndEnforcesLeaseConstraints() {
        assertThat(payments.findById(OLD).orElseThrow().getAmount()).isEqualByComparingTo(new BigDecimal("199000"));
        assertThat(payments.findById(OLD).orElseThrow().getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(payments.findById(OLD).orElseThrow().getCreatedAt())
                .isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version='26' AND success=1", Integer.class))
                .isEqualTo(1);
        var payment = payment(now.minusSeconds(1200));
        store.claim(now).orElseThrow();
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE thanh_toan_doi_soat SET query_count=-1 WHERE payment_attempt_id=?",
                        ReconciliationRepository.bytes(payment.getId())))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE thanh_toan_doi_soat SET lease_token=NULL WHERE payment_attempt_id=?",
                        ReconciliationRepository.bytes(payment.getId())))
                .isInstanceOf(DataAccessException.class);
    }
}
