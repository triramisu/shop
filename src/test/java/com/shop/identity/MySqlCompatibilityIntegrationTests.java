package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.identity.internal.captcha.service.AdaptiveCaptchaService;
import com.shop.identity.internal.dto.request.AuthenticationRequest;
import com.shop.identity.internal.dto.request.ChangePasswordRequest;
import com.shop.identity.internal.dto.request.IntrospectRequest;
import com.shop.identity.internal.dto.request.RefreshRequest;
import com.shop.identity.internal.dto.request.RegisterUserRequest;
import com.shop.identity.internal.dto.request.UpdateProfileRequest;
import com.shop.identity.internal.dto.response.AuthenticationResponse;
import com.shop.identity.internal.dto.response.IntrospectResponse;
import com.shop.identity.internal.dto.response.UserResponse;
import com.shop.identity.internal.service.AuthenticationService;
import com.shop.identity.internal.service.RegistrationService;
import com.shop.identity.internal.service.UserProfileService;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
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

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserProfileService userProfileService;

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
                "SELECT COUNT(*) FROM xac_thuc_vai_tro WHERE code IN ('ADMIN', 'STAFF', 'USER')", Integer.class);

        assertThat(databaseVersion).startsWith("8.0.");
        assertThat(migrationCount).isEqualTo(8);
        assertThat(defaultRoleCount).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("SELECT version FROM xac_thuc_vai_tro", Long.class))
                .containsOnly(0L);
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

    @Test
    void updatesProfilesAndRevokesSessionsOnMySql() {
        String username = "mysql-profile-user";
        String currentPassword = "Str0ngPassword!";
        String newPassword = "An0therStrongPassword!";
        registrationService.register(RegisterUserRequest.builder()
                .username(username)
                .email("mysql-profile-user@example.com")
                .password(currentPassword)
                .build());
        AuthenticationResponse session = authenticationService.authenticate(AuthenticationRequest.builder()
                .username(username)
                .password(currentPassword)
                .build());

        UserResponse updatedProfile = userProfileService.updateProfile(
                username,
                UpdateProfileRequest.builder()
                        .email("updated-mysql-profile@example.com")
                        .firstName("MySQL")
                        .build());
        userProfileService.changePassword(
                username,
                ChangePasswordRequest.builder()
                        .currentPassword(currentPassword)
                        .newPassword(newPassword)
                        .build());

        assertThat(updatedProfile.getEmail()).isEqualTo("updated-mysql-profile@example.com");
        Integer activeSessions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM xac_thuc_phien_lam_moi refresh_token
                  JOIN xac_thuc_nguoi_dung shop_user ON shop_user.id = refresh_token.user_id
                 WHERE shop_user.username = ?
                   AND refresh_token.revoked_at IS NULL
                """, Integer.class, username);
        assertThat(activeSessions).isZero();
        assertThat(session.getRefreshToken()).isNotBlank();
        assertThat(authenticationService
                        .authenticate(AuthenticationRequest.builder()
                                .username(username)
                                .password(newPassword)
                                .build())
                        .isAuthenticated())
                .isTrue();
    }

    @Test
    void serializesConcurrentRefreshesAndRevokesTheReplayedTokenFamily() throws Exception {
        String username = "mysql-refresh-concurrency";
        registrationService.register(RegisterUserRequest.builder()
                .username(username)
                .email("mysql-refresh-concurrency@example.com")
                .password("Str0ngPassword!")
                .build());
        AuthenticationResponse session = authenticationService.authenticate(AuthenticationRequest.builder()
                .username(username)
                .password("Str0ngPassword!")
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<RefreshAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<RefreshAttempt>> futures = IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        return attemptRefresh(session.getRefreshToken());
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = futures.stream().map(this::getAttempt).toList();
        }

        assertThat(attempts).filteredOn(attempt -> attempt.response() != null).hasSize(1);
        assertThat(attempts)
                .filteredOn(attempt -> attempt.errorCode() == ErrorCode.REFRESH_TOKEN_REUSED)
                .hasSize(1);
        AuthenticationResponse rotatedSession = attempts.stream()
                .map(RefreshAttempt::response)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
        IntrospectResponse introspection = authenticationService.introspect(IntrospectRequest.builder()
                .token(rotatedSession.getAccessToken())
                .build());
        assertThat(introspection.isValid()).isFalse();

        Integer activeSessions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM xac_thuc_phien_lam_moi refresh_token
                  JOIN xac_thuc_nguoi_dung shop_user ON shop_user.id = refresh_token.user_id
                 WHERE shop_user.username = ?
                   AND refresh_token.revoked_at IS NULL
                """, Integer.class, username);
        assertThat(activeSessions).isZero();
    }

    private RefreshAttempt attemptRefresh(String refreshToken) {
        try {
            return new RefreshAttempt(
                    authenticationService.refreshToken(
                            RefreshRequest.builder().token(refreshToken).build()),
                    null);
        } catch (AppException exception) {
            return new RefreshAttempt(null, exception.getErrorCode());
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected refresh failure", exception);
        }
    }

    private RefreshAttempt getAttempt(Future<RefreshAttempt> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent refresh did not complete", exception);
        }
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

    private record RefreshAttempt(AuthenticationResponse response, ErrorCode errorCode) {}
}
