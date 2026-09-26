package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.identity.internal.captcha.service.AdaptiveCaptchaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MySqlCompatibilityIntegrationTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_test")
            .withUsername("shop_test")
            .withPassword("shop-test-password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdaptiveCaptchaService adaptiveCaptchaService;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("app.security.captcha.enabled", () -> "true");
    }

    @Test
    void appliesFlywayMigrationsAndValidatesTheMySqlSchema() {
        String databaseVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        Integer defaultRoleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xac_thuc_vai_tro WHERE code IN ('ADMIN', 'USER')", Integer.class);

        assertThat(databaseVersion).startsWith("8.0.");
        assertThat(migrationCount).isEqualTo(5);
        assertThat(defaultRoleCount).isEqualTo(2);
    }

    @Test
    void keepsCaptchaStateConsistentUnderConcurrentMySqlWrites() throws Exception {
        String username = "mysql-captcha-concurrency";
        int concurrentFailures = 8;
        CountDownLatch ready = new CountDownLatch(concurrentFailures);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrentFailures)) {
            List<? extends Future<?>> futures = IntStream.range(0, concurrentFailures)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        adaptiveCaptchaService.recordFailure(username);
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        String principalHash = hash(username);
        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT failure_count FROM xac_thuc_dang_nhap_that_bai WHERE principal_hash = ?",
                Integer.class,
                principalHash);
        assertThat(failureCount).isEqualTo(concurrentFailures);

        adaptiveCaptchaService.issueChallenge(username);
        adaptiveCaptchaService.issueChallenge(username);
        Integer activeChallenges = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xac_thuc_thu_thach_captcha WHERE principal_hash = ?",
                Integer.class,
                principalHash);
        assertThat(activeChallenges).isEqualTo(1);
    }

    private String hash(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start concurrent MySQL writes", exception);
        }
    }
}
