package com.shop.payment.internal.refund.service;

import static com.shop.payment.internal.refund.service.RefundMigrationFixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.internal.refund.entity.RefundStatus;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.provider.RefundProviderResult;
import com.shop.payment.provider.RefundProviderStatus;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefundMySqlTests {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("refund_core_test")
            .withUsername("refund_test")
            .withPassword("refund-local-test-password");

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration", "classpath:db/mysql")
                .target("24")
                .load()
                .migrate();
        RefundMigrationFixtures.seedV24(new JdbcTemplate(source));
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
    }

    @Autowired
    RefundTransactionService transactions;

    @Autowired
    PaymentAttemptRepository payments;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void upgradesV24DataAndKeepsForeignKeysInsidePayment() {
        RefundMigrationFixtures.assertUpgraded(jdbc);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version='25' AND success=1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "SELECT referenced_table_name FROM information_schema.key_column_usage "
                                + "WHERE table_schema=DATABASE() AND table_name IN ('thanh_toan_ngan_sach_hoan','thanh_toan_lan_hoan') "
                                + "AND referenced_table_name IS NOT NULL",
                        String.class))
                .containsExactlyInAnyOrder("thanh_toan_lan_thu", "thanh_toan_ngan_sach_hoan");
    }

    @Test
    void twoDifferentKeysCannotReserveMoreThanCapturedUnderRealMysqlLocking() throws Exception {
        for (int round = 0; round < 3; round++) {
            UUID id = payment();
            List<Object> outcomes = race(id, "first", "second");
            assertThat(outcomes).filteredOn(PreparedRefund.class::isInstance).hasSize(1);
            assertThat(outcomes)
                    .filteredOn(AppException.class::isInstance)
                    .singleElement()
                    .satisfies(value -> assertThat(((AppException) value).getErrorCode())
                            .isEqualTo(ErrorCode.PAYMENT_REFUND_LIMIT_EXCEEDED));
            assertMoney(id, "60", "0");
            assertThat(countRefunds(id)).isEqualTo(1);
        }
    }

    @Test
    void duplicateConcurrentKeysReserveOnlyOnceAndCrashReplayNeverIssuesANewProviderRequest() throws Exception {
        UUID id = payment();
        List<Object> outcomes = race(id, "same", "same");
        assertThat(outcomes).allMatch(PreparedRefund.class::isInstance);
        var prepared = outcomes.stream().map(PreparedRefund.class::cast).toList();
        assertThat(prepared)
                .extracting(p -> p.snapshot().id())
                .containsOnly(prepared.getFirst().snapshot().id());
        assertThat(prepared).filteredOn(p -> p.request() != null).hasSize(1);
        var replay = prepare(id, "same");
        assertThat(replay.request()).isNull();
        assertThat(replay.snapshot().status()).isEqualTo(RefundStatus.CREATED);
        assertMoney(id, "60", "0");
        assertThat(countRefunds(id)).isEqualTo(1);
    }

    @Test
    void providerCompletionReplayAndLateFailureCannotDoubleSettleOrDowngrade() {
        UUID id = payment();
        var refund = prepare(id, "settlement");
        UUID refundId = refund.snapshot().id();
        transactions.apply(id, refundId, new RefundProviderResult(RefundProviderStatus.UNKNOWN, null, null));
        assertMoney(id, "60", "0");
        var success = new RefundProviderResult(RefundProviderStatus.SUCCEEDED, "re_" + refundId, null);
        transactions.apply(id, refundId, success);
        transactions.apply(id, refundId, success);
        var stale = transactions.apply(
                id, refundId, new RefundProviderResult(RefundProviderStatus.FAILED, "re_" + refundId, "DECLINED"));
        assertThat(stale.status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertMoney(id, "60", "60");
    }

    @Test
    void databaseConstraintsRejectOverAllocationAndDuplicateKeysAndInvalidState() {
        UUID id = payment();
        var refund = prepare(id, "constraints");
        for (String assignment : new String[] {"allocated_amount=101", "succeeded_amount=61", "allocated_amount=-1"}) {
            assertMysqlRejects(
                    () -> jdbc.update(
                            "UPDATE thanh_toan_ngan_sach_hoan SET " + assignment + " WHERE payment_attempt_id=?",
                            bytes(id)),
                    3819);
        }
        assertMysqlRejects(
                () -> jdbc.update(
                        "UPDATE thanh_toan_lan_hoan SET status='SUCCEEDED' WHERE id=?",
                        bytes(refund.snapshot().id())),
                3819);
        assertMysqlRejects(
                () -> jdbc.update(
                        "INSERT INTO thanh_toan_lan_hoan SELECT ?, payment_attempt_id, order_id, provider_code, "
                                + "provider_payment_reference, provider_reference, key_hash, fingerprint, actor_subject, amount, currency, status, failure_code, version, created_at, updated_at "
                                + "FROM thanh_toan_lan_hoan WHERE id=?",
                        bytes(UUID.randomUUID()),
                        bytes(refund.snapshot().id())),
                1062);
        assertMoney(id, "60", "0");
        assertThat(countRefunds(id)).isEqualTo(1);
    }

    @Test
    void keyHashesPreserveCaseUnderMysqlCollationAndRollbacksDoNotConsumeBudget() {
        UUID id = payment();
        transactions.prepare(id, RefundCommand.of(new BigDecimal("40"), "USD", "Key"), "test-admin", Set.of("FAKE"));
        transactions.prepare(id, RefundCommand.of(new BigDecimal("40"), "USD", "key"), "test-admin", Set.of("FAKE"));
        assertMoney(id, "80", "0");
        assertThatThrownBy(() -> transactions.prepare(
                        id,
                        RefundCommand.of(new BigDecimal("1"), "USD", "invalid-actor"),
                        "a".repeat(101),
                        Set.of("FAKE")))
                .isInstanceOf(IllegalArgumentException.class);
        assertMoney(id, "80", "0");
        assertThat(countRefunds(id)).isEqualTo(2);
    }

    private List<Object> race(UUID id, String first, String second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = List.of(first, second).stream()
                    .map(key -> pool.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("Không mở được barrier test");
                        try {
                            return (Object) prepare(id, key);
                        } catch (AppException exception) {
                            return exception;
                        }
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private UUID payment() {
        return payments.saveAndFlush(RefundTestFixtures.payment("FAKE", true)).getId();
    }

    private PreparedRefund prepare(UUID id, String key) {
        return transactions.prepare(
                id, RefundCommand.of(new BigDecimal("60"), "USD", key), "test-admin", Set.of("FAKE"));
    }

    private int countRefunds(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thanh_toan_lan_hoan WHERE payment_attempt_id=?", Integer.class, bytes(id));
    }

    private void assertMoney(UUID id, String allocated, String succeeded) {
        assertThat(jdbc.queryForObject(
                        "SELECT allocated_amount FROM thanh_toan_ngan_sach_hoan WHERE payment_attempt_id=?",
                        BigDecimal.class,
                        bytes(id)))
                .isEqualByComparingTo(allocated);
        assertThat(jdbc.queryForObject(
                        "SELECT succeeded_amount FROM thanh_toan_ngan_sach_hoan WHERE payment_attempt_id=?",
                        BigDecimal.class,
                        bytes(id)))
                .isEqualByComparingTo(succeeded);
    }

    private void assertMysqlRejects(ThrowingCallable action, int errorCode) {
        assertThatThrownBy(action).isInstanceOf(DataAccessException.class).satisfies(exception -> {
            Throwable root = ((DataAccessException) exception).getMostSpecificCause();
            assertThat(root).isInstanceOf(SQLException.class);
            assertThat(((SQLException) root).getErrorCode()).isEqualTo(errorCode);
        });
    }
}
