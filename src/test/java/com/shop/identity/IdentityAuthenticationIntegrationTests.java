package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import com.shop.identity.internal.service.RefreshTokenCleanupService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdentityAuthenticationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenCleanupService refreshTokenCleanupService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void logsInWithSeparateTokensAndAuthorizesAccessToken() throws Exception {
        registerUser("auth-user", "auth-user@example.com");

        TokenPair tokens = authenticate("auth-user", "Str0ngPassword!");

        assertThat(tokens.accessToken()).isNotBlank().isNotEqualTo(tokens.refreshToken());
        assertThat(tokens.refreshToken()).isNotBlank();

        SignedJWT accessJwt = SignedJWT.parse(tokens.accessToken());
        SignedJWT refreshJwt = SignedJWT.parse(tokens.refreshToken());
        assertThat(accessJwt.getJWTClaimsSet().getStringClaim("token_type")).isEqualTo("access");
        assertThat(accessJwt.getJWTClaimsSet().getStringClaim("scope")).contains("ROLE_USER");
        assertThat(refreshJwt.getJWTClaimsSet().getStringClaim("token_type")).isEqualTo("refresh");
        assertThat(refreshJwt.getJWTClaimsSet().getClaim("scope")).isNull();
        assertThat(accessJwt.getJWTClaimsSet().getStringClaim("family_id"))
                .isEqualTo(refreshJwt.getJWTClaimsSet().getStringClaim("family_id"));
        assertThat(ChronoUnit.SECONDS.between(
                        accessJwt.getJWTClaimsSet().getIssueTime().toInstant(),
                        accessJwt.getJWTClaimsSet().getExpirationTime().toInstant()))
                .isEqualTo(3600);
        assertThat(ChronoUnit.SECONDS.between(
                        refreshJwt.getJWTClaimsSet().getIssueTime().toInstant(),
                        refreshJwt.getJWTClaimsSet().getExpirationTime().toInstant()))
                .isEqualTo(36000);

        List<String> hashes = jdbcTemplate.queryForList("SELECT token_hash FROM xac_thuc_phien_lam_moi", String.class);
        assertThat(hashes).hasSize(1);
        assertThat(hashes.getFirst()).hasSize(64).isNotEqualTo(tokens.refreshToken());

        mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void introspectsOnlyValidAccessTokens() throws Exception {
        registerUser("introspect-user", "introspect-user@example.com");
        TokenPair tokens = authenticate("introspect-user", "Str0ngPassword!");

        introspect(tokens.accessToken(), true);
        introspect(tokens.refreshToken(), false);
        introspect(tokens.accessToken() + "tampered", false);
    }

    @Test
    void rotatesRefreshTokenAndRejectsReuse() throws Exception {
        registerUser("rotation-user", "rotation-user@example.com");
        TokenPair first = authenticate("rotation-user", "Str0ngPassword!");

        TokenPair second = refresh(first.refreshToken());
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(first.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1015));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(second.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1014));

        Integer revokedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xac_thuc_phien_lam_moi WHERE revoked_at IS NOT NULL", Integer.class);
        assertThat(revokedCount).isEqualTo(2);
    }

    @Test
    void rejectsInvalidCredentialsWithoutRevealingUserExistence() throws Exception {
        registerUser("credential-user", "credential-user@example.com");

        assertUnauthenticated("credential-user", "wrong-password");
        assertUnauthenticated("missing-user", "wrong-password");
    }

    @Test
    void doesNotRevealDisabledAccountBeforePasswordIsValidated() throws Exception {
        registerUser("disabled-user", "disabled-user@example.com");
        jdbcTemplate.update("UPDATE xac_thuc_nguoi_dung SET status = 'DISABLED' WHERE username = ?", "disabled-user");
        entityManager.clear();

        assertUnauthenticated("disabled-user", "wrong-password");

        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "disabled-user",
                                  "password": "Str0ngPassword!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1017));
    }

    @Test
    void logsOutIdempotentlyAndRevokesTheWholeTokenFamily() throws Exception {
        registerUser("logout-user", "logout-user@example.com");
        TokenPair tokens = authenticate("logout-user", "Str0ngPassword!");

        logout(tokens.refreshToken());
        logout(tokens.refreshToken());

        mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
        introspect(tokens.accessToken(), false);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(tokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1014));

        Integer revokedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xac_thuc_phien_lam_moi WHERE revoked_at IS NOT NULL", Integer.class);
        assertThat(revokedCount).isEqualTo(1);
    }

    @Test
    void rejectsMalformedLogoutTokensWithTheApiErrorContract() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1014))
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));
    }

    @Test
    void deletesOnlyExpiredRefreshTokens() throws Exception {
        registerUser("expired-token-user", "expired-token-user@example.com");
        authenticate("expired-token-user", "Str0ngPassword!");
        registerUser("active-token-user", "active-token-user@example.com");
        authenticate("active-token-user", "Str0ngPassword!");

        jdbcTemplate.update("""
                UPDATE xac_thuc_phien_lam_moi
                   SET expires_at = ?
                 WHERE user_id = (SELECT id FROM xac_thuc_nguoi_dung WHERE username = ?)
                """, Timestamp.from(Instant.now().minusSeconds(1)), "expired-token-user");

        int deletedCount = refreshTokenCleanupService.deleteExpiredTokens(Instant.now());

        assertThat(deletedCount).isEqualTo(1);
        Integer remainingCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM xac_thuc_phien_lam_moi", Integer.class);
        assertThat(remainingCount).isEqualTo(1);
    }

    private void registerUser(String username, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "Str0ngPassword!"
                                }
                                """.formatted(username, email)))
                .andExpect(status().isCreated());
    }

    private TokenPair authenticate(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.authenticated").value(true))
                .andExpect(jsonPath("$.result.tokenType").value("Bearer"))
                .andReturn();
        return readTokenPair(result);
    }

    private TokenPair refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.authenticated").value(true))
                .andReturn();
        return readTokenPair(result);
    }

    private void introspect(String token, boolean expectedValid) throws Exception {
        mockMvc.perform(post("/api/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.valid").value(expectedValid));
    }

    private void logout(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    private void assertUnauthenticated(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private TokenPair readTokenPair(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.result.accessToken");
        String refreshToken = JsonPath.read(body, "$.result.refreshToken");
        return new TokenPair(accessToken, refreshToken);
    }

    private String tokenJson(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }

    private record TokenPair(String accessToken, String refreshToken) {}
}
