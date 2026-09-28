package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
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
class IdentityUserProfileIntegrationTests {

    private static final String CURRENT_PASSWORD = "Str0ngPassword!";
    private static final String NEW_PASSWORD = "An0therStrongPassword!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void requiresAnAccessTokenForEveryProfileOperation() throws Exception {
        mockMvc.perform(get("/api/auth/my-info")).andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/auth/my-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProfileJson("anonymous@example.com")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/auth/my-info/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersProfile() throws Exception {
        registerUser("profile-owner", "profile-owner@example.com");
        registerUser("another-user", "another-user@example.com");
        TokenPair tokens = authenticate("profile-owner", CURRENT_PASSWORD);

        mockMvc.perform(get("/api/auth/my-info").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.username").value("profile-owner"))
                .andExpect(jsonPath("$.result.email").value("profile-owner@example.com"))
                .andExpect(jsonPath("$.result.roles[0]").value("USER"))
                .andExpect(jsonPath("$.result.password").doesNotExist())
                .andExpect(jsonPath("$.result.passwordHash").doesNotExist());
    }

    @Test
    void updatesTheAuthenticatedUsersProfileAndResetsEmailVerification() throws Exception {
        registerUser("profile-update", "old-email@example.com");
        jdbcTemplate.update(
                "UPDATE xac_thuc_nguoi_dung SET email_verified = TRUE WHERE username = ?", "profile-update");
        entityManager.clear();
        TokenPair tokens = authenticate("profile-update", CURRENT_PASSWORD);

        mockMvc.perform(put("/api/auth/my-info")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Updated.Email@Example.COM",
                                  "firstName": "  Updated  ",
                                  "lastName": "   ",
                                  "dateOfBirth": "1994-04-12"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.username").value("profile-update"))
                .andExpect(jsonPath("$.result.email").value("updated.email@example.com"))
                .andExpect(jsonPath("$.result.firstName").value("Updated"))
                .andExpect(jsonPath("$.result.lastName").doesNotExist())
                .andExpect(jsonPath("$.result.dateOfBirth").value("1994-04-12"))
                .andExpect(jsonPath("$.result.emailVerified").value(false));

        String storedEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM xac_thuc_nguoi_dung WHERE username = ?", String.class, "profile-update");
        assertThat(storedEmail).isEqualTo("updated.email@example.com");
    }

    @Test
    void updatesOnlyTheProfileResolvedFromTheAuthenticatedPrincipal() throws Exception {
        registerUser("ownership-actor", "ownership-actor@example.com");
        registerUser("ownership-target", "ownership-target@example.com");
        TokenPair actorTokens = authenticate("ownership-actor", CURRENT_PASSWORD);

        mockMvc.perform(put("/api/auth/my-info")
                        .header(HttpHeaders.AUTHORIZATION, bearer(actorTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProfileJson("ownership-actor-updated@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.username").value("ownership-actor"))
                .andExpect(jsonPath("$.result.email").value("ownership-actor-updated@example.com"));

        assertThat(emailOf("ownership-actor")).isEqualTo("ownership-actor-updated@example.com");
        assertThat(emailOf("ownership-target")).isEqualTo("ownership-target@example.com");
    }

    @Test
    void rejectsAnotherUsersEmailWithoutChangingTheProfile() throws Exception {
        registerUser("email-owner", "email-owner@example.com");
        registerUser("profile-editor", "profile-editor@example.com");
        TokenPair tokens = authenticate("profile-editor", CURRENT_PASSWORD);

        mockMvc.perform(put("/api/auth/my-info")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProfileJson("email-owner@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1009));

        mockMvc.perform(get("/api/auth/my-info").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").value("profile-editor@example.com"));
    }

    @Test
    void changesPasswordAndRevokesEveryExistingSession() throws Exception {
        registerUser("password-owner", "password-owner@example.com");
        TokenPair tokens = authenticate("password-owner", CURRENT_PASSWORD);

        mockMvc.perform(put("/api/auth/my-info/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result").doesNotExist());

        mockMvc.perform(get("/api/auth/my-info").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(tokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1014));

        assertLoginFails("password-owner", CURRENT_PASSWORD);
        authenticate("password-owner", NEW_PASSWORD);
    }

    @Test
    void rejectsAnIncorrectCurrentPasswordWithoutRevokingTheSession() throws Exception {
        registerUser("wrong-current-password", "wrong-current-password@example.com");
        TokenPair tokens = authenticate("wrong-current-password", CURRENT_PASSWORD);

        mockMvc.perform(put("/api/auth/my-info/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson("WrongPassword!", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1029));

        mockMvc.perform(get("/api/auth/my-info").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk());
        authenticate("wrong-current-password", CURRENT_PASSWORD);
    }

    @Test
    void rejectsReusingTheCurrentPassword() throws Exception {
        registerUser("same-password", "same-password@example.com");
        TokenPair tokens = authenticate("same-password", CURRENT_PASSWORD);

        mockMvc.perform(put("/api/auth/my-info/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson(CURRENT_PASSWORD, CURRENT_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1030));
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
                                """.formatted(username, email, CURRENT_PASSWORD)))
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
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new TokenPair(JsonPath.read(body, "$.result.accessToken"), JsonPath.read(body, "$.result.refreshToken"));
    }

    private void assertLoginFails(String username, String password) throws Exception {
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

    private String emailOf(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT email FROM xac_thuc_nguoi_dung WHERE username = ?", String.class, username);
    }

    private String validProfileJson(String email) {
        return """
                {
                  "email": "%s",
                  "firstName": "Profile",
                  "lastName": "Owner",
                  "dateOfBirth": "1990-01-01"
                }
                """.formatted(email);
    }

    private String changePasswordJson(String currentPassword, String newPassword) {
        return """
                {
                  "currentPassword": "%s",
                  "newPassword": "%s"
                }
                """.formatted(currentPassword, newPassword);
    }

    private String tokenJson(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TokenPair(String accessToken, String refreshToken) {}
}
