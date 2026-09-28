package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdentityRegistrationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registersUserWithEncodedPasswordAndDefaultRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "customer.one",
                                  "email": "Customer.One@Example.com",
                                  "password": "Str0ngPassword!",
                                  "firstName": "Customer",
                                  "lastName": "One",
                                  "dateOfBirth": "1995-05-20"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.username").value("customer.one"))
                .andExpect(jsonPath("$.result.email").value("customer.one@example.com"))
                .andExpect(jsonPath("$.result.roles[0]").value("USER"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.password").doesNotExist());

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM xac_thuc_nguoi_dung WHERE username = ?", String.class, "customer.one");
        Integer roleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM xac_thuc_nguoi_dung_vai_tro ur
                JOIN xac_thuc_vai_tro r ON r.code = ur.role_code
                JOIN xac_thuc_nguoi_dung u ON u.id = ur.user_id
                WHERE u.username = ? AND r.code = 'USER'
                """, Integer.class, "customer.one");

        assertThat(passwordHash).startsWith("$2").doesNotContain("Str0ngPassword!");
        assertThat(roleCount).isEqualTo(1);
    }

    @Test
    void rejectsInvalidRegistrationWithApiResponse() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bad user",
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void returnsSpecificMessagesForEachValidationConstraint() throws Exception {
        assertValidationError(
                """
                {
                  "username": "bad user",
                  "email": "valid@example.com",
                  "password": "Str0ngPassword!"
                }
                """, 1020, "Tên đăng nhập chỉ được chứa chữ cái, chữ số, dấu chấm, dấu gạch dưới và dấu gạch ngang");

        assertValidationError("""
                {
                  "username": null,
                  "email": "valid@example.com",
                  "password": "Str0ngPassword!"
                }
                """, 1019, "Tên đăng nhập là bắt buộc");

        assertValidationError("""
                {
                  "username": "valid-user",
                  "email": "valid@example.com",
                  "password": "Str0ngPassword!",
                  "firstName": "%s"
                }
                """.formatted("x".repeat(101)), 1022, "Tên không được vượt quá 100 ký tự");
    }

    @Test
    void acceptsCorsPreflightOnlyForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/token")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/auth/token")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        register("same-name", "first@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("same-name", "second@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void protectsNonPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(username, email)))
                .andExpect(status().isCreated());
    }

    private void assertValidationError(String request, int code, String message) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    private String registrationJson(String username, String email) {
        return """
                {
                  "username": "%s",
                  "email": "%s",
                  "password": "Str0ngPassword!"
                }
                """.formatted(username, email);
    }
}
