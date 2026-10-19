package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.identity.internal.captcha.service.AdaptiveCaptchaCleanupService;
import com.shop.identity.internal.captcha.service.AdaptiveCaptchaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
        properties = {
            "app.security.captcha.enabled=true",
            "app.security.captcha.failure-threshold=3",
            "app.security.captcha.failure-window=15m",
            "app.security.captcha.challenge-ttl=2m",
            "app.security.captcha.max-active-challenges=2",
            "app.security.rate-limit.enabled=false"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdaptiveCaptchaIntegrationTests {

    private static final String PASSWORD = "Str0ngPassword!";
    private static final String CAPTCHA_ANSWER = "ABC234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdaptiveCaptchaCleanupService adaptiveCaptchaCleanupService;

    @Autowired
    private AdaptiveCaptchaService adaptiveCaptchaService;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void requiresCaptchaAfterFailedLoginsAndConsumesEveryChallengeOnce() throws Exception {
        String username = "captcha-user";
        registerUser(username, "captcha-user@example.com");

        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");

        login(username, PASSWORD, null, null)
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(1025));

        Captcha issued = issueCaptcha(username);
        setKnownAnswer(issued.id(), CAPTCHA_ANSWER);
        login(username, PASSWORD, issued.id(), "WRONG1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1026));
        login(username, PASSWORD, issued.id(), CAPTCHA_ANSWER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1026));

        Captcha consumedByFailedLogin = issueCaptcha(username);
        setKnownAnswer(consumedByFailedLogin.id(), CAPTCHA_ANSWER);
        login(username, "wrong-password", consumedByFailedLogin.id(), CAPTCHA_ANSWER)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
        login(username, PASSWORD, consumedByFailedLogin.id(), CAPTCHA_ANSWER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1026));

        Captcha valid = issueCaptcha(username);
        setKnownAnswer(valid.id(), CAPTCHA_ANSWER);
        login(username, PASSWORD, valid.id(), CAPTCHA_ANSWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.authenticated").value(true));

        String principalHash = hash(username.strip().toLowerCase(Locale.ROOT));
        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xac_thuc_dang_nhap_that_bai WHERE principal_hash = ?",
                Integer.class,
                principalHash);
        Integer challengeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xac_thuc_thu_thach_captcha WHERE principal_hash = ?",
                Integer.class,
                principalHash);
        assertThat(failureCount).isZero();
        assertThat(challengeCount).isZero();

        login(username, PASSWORD, null, null).andExpect(status().isOk());
    }

    @Test
    void appliesTheSameCaptchaPolicyToUnknownUsernames() throws Exception {
        String username = "unknown-captcha-user";

        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");

        login(username, "wrong-password", null, null)
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(1025));
    }

    @Test
    void issuesCaptchaOnlyAfterTheFailureThreshold() throws Exception {
        String username = "captcha-threshold-user";

        requestCaptcha(username)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1028));

        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");
        requestCaptcha(username)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1028));

        failLogin(username, "wrong-password");
        issueCaptcha(username);
    }

    @Test
    void recordsConcurrentFailuresAtomically() throws Exception {
        String username = "captcha-concurrent-failures";
        int concurrentFailures = 12;
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

        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT failure_count FROM xac_thuc_dang_nhap_that_bai WHERE principal_hash = ?",
                Integer.class,
                hash(username));
        assertThat(failureCount).isEqualTo(concurrentFailures);
    }

    @Test
    void serializesChallengeIssuanceAndEnforcesTheGlobalCapacity() throws Exception {
        requireCaptchaFor("captcha-capacity-a");
        requireCaptchaFor("captcha-capacity-b");
        requireCaptchaFor("captcha-capacity-c");

        issueCaptcha("captcha-capacity-a");
        issueCaptcha("captcha-capacity-b");
        requestCaptcha("captcha-capacity-c")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1027));

        issueCaptcha("captcha-capacity-a");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM xac_thuc_thu_thach_captcha", Integer.class))
                .isEqualTo(2);

        String username = "captcha-concurrent-challenges";
        cleanDatabase();
        requireCaptchaFor(username);
        int concurrentRequests = 6;
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests)) {
            List<? extends Future<?>> futures = IntStream.range(0, concurrentRequests)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        adaptiveCaptchaService.issueChallenge(username);
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(rowCount("xac_thuc_thu_thach_captcha", hash(username))).isEqualTo(1);
    }

    @Test
    void rejectsExpiredCaptcha() throws Exception {
        String username = "expired-captcha-user";
        registerUser(username, "expired-captcha-user@example.com");
        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");

        Captcha captcha = issueCaptcha(username);
        setKnownAnswer(captcha.id(), CAPTCHA_ANSWER);
        jdbcTemplate.update(
                "UPDATE xac_thuc_thu_thach_captcha SET expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) WHERE captcha_id = ?",
                captcha.id());

        login(username, PASSWORD, captcha.id(), CAPTCHA_ANSWER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1026));
    }

    @Test
    void removesExpiredChallengesAndFailureRecords() throws Exception {
        String username = "captcha-cleanup-user";
        requireCaptchaFor(username);
        Captcha captcha = issueCaptcha(username);
        String principalHash = hash(username);

        jdbcTemplate.update(
                "UPDATE xac_thuc_thu_thach_captcha SET expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) WHERE captcha_id = ?",
                captcha.id());
        jdbcTemplate.update(
                "UPDATE xac_thuc_dang_nhap_that_bai SET expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) WHERE principal_hash = ?",
                principalHash);

        adaptiveCaptchaCleanupService.cleanupExpiredState();

        assertThat(rowCount("xac_thuc_thu_thach_captcha", principalHash)).isZero();
        assertThat(rowCount("xac_thuc_dang_nhap_that_bai", principalHash)).isZero();
    }

    private void registerUser(String username, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private void failLogin(String username, String password) throws Exception {
        login(username, password, null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String username, String password, String captchaId, String captchaAnswer) throws Exception {
        String captchaFields = captchaId == null
                ? ""
                : ",\"captchaId\":\"%s\",\"captchaAnswer\":\"%s\"".formatted(captchaId, captchaAnswer);
        return mockMvc.perform(
                post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","password":"%s"%s}
                        """.formatted(
                                username, password, captchaFields)));
    }

    private Captcha issueCaptcha(String username) throws Exception {
        MvcResult result = requestCaptcha(username)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.captchaId").isNotEmpty())
                .andExpect(jsonPath("$.result.imageData")
                        .value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.result.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.result.text").doesNotExist())
                .andExpect(jsonPath("$.result.answer").doesNotExist())
                .andReturn();
        String captchaId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.captchaId");
        String storedPrincipalHash = jdbcTemplate.queryForObject(
                "SELECT principal_hash FROM xac_thuc_thu_thach_captcha WHERE captcha_id = ?", String.class, captchaId);
        String storedAnswerHash = jdbcTemplate.queryForObject(
                "SELECT answer_hash FROM xac_thuc_thu_thach_captcha WHERE captcha_id = ?", String.class, captchaId);
        assertThat(storedPrincipalHash).hasSize(64).isNotEqualTo(username);
        assertThat(storedAnswerHash).hasSize(64);
        return new Captcha(captchaId);
    }

    private org.springframework.test.web.servlet.ResultActions requestCaptcha(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/captcha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s"}
                        """.formatted(username)));
    }

    private void requireCaptchaFor(String username) throws Exception {
        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");
        failLogin(username, "wrong-password");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting to start a concurrent captcha operation", exception);
        }
    }

    private void setKnownAnswer(String captchaId, String answer) throws Exception {
        String value = captchaId + ':' + answer.strip().toUpperCase(Locale.ROOT);
        String answerHash = hash(value);
        assertThat(jdbcTemplate.update(
                        "UPDATE xac_thuc_thu_thach_captcha SET answer_hash = ? WHERE captcha_id = ?",
                        answerHash,
                        captchaId))
                .isEqualTo(1);
    }

    private String hash(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private int rowCount(String table, String principalHash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE principal_hash = ?", Integer.class, principalHash);
        return count == null ? 0 : count;
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM xac_thuc_thu_thach_captcha");
        jdbcTemplate.update("DELETE FROM xac_thuc_dang_nhap_that_bai");
        jdbcTemplate.update("DELETE FROM xac_thuc_phien_lam_moi");
        jdbcTemplate.update("DELETE FROM xac_thuc_nguoi_dung_vai_tro");
        jdbcTemplate.update("DELETE FROM xac_thuc_nguoi_dung");
    }

    private record Captcha(String id) {}
}
