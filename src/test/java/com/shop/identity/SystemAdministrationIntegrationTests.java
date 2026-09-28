package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.identity.internal.constant.RoleCode;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
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
class SystemAdministrationIntegrationTests {

    private static final String PASSWORD = "Str0ngPassword!";
    private static final String BASE = "/api/system-administration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void allowsStaffToSearchUsersButRejectsAnOrdinaryUser() throws Exception {
        Account staff = accountWithRole("search-staff", RoleCode.STAFF);
        Account customer = accountWithRole("search-customer", RoleCode.USER);

        mockMvc.perform(get(BASE + "/users")
                        .param("keyword", "search-")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content.length()").value(2))
                .andExpect(jsonPath("$.result.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.result.totalElements").value(2));

        mockMvc.perform(get(BASE + "/users").header(HttpHeaders.AUTHORIZATION, bearer(customer.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        mockMvc.perform(get(BASE + "/users")
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1048));

        mockMvc.perform(get(BASE + "/users")
                        .param("status", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1010));
    }

    @Test
    void returnsTheMalformedRequestContractForAnInvalidUserId() throws Exception {
        Account staff = accountWithRole("invalid-id-staff", RoleCode.STAFF);

        mockMvc.perform(get(BASE + "/users/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1010))
                .andExpect(jsonPath("$.message").value("Dữ liệu yêu cầu không đúng định dạng"));
    }

    @Test
    void letsStaffAssignASafeCustomRoleAndRevokeTheUsersExistingSession() throws Exception {
        Account admin = accountWithRole("role-admin", RoleCode.ADMIN);
        Account staff = accountWithRole("role-staff", RoleCode.STAFF);
        Account customer = accountWithRole("role-customer", RoleCode.USER);

        createRole(admin.accessToken(), "SUPPORT", Set.of("SYSTEM_USER_READ"));

        mockMvc.perform(put(BASE + "/users/{userId}/roles", customer.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":["USER","SUPPORT"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.roles.length()").value(2))
                .andExpect(jsonPath("$.result.roles[0]").value("SUPPORT"))
                .andExpect(jsonPath("$.result.roles[1]").value("USER"));

        mockMvc.perform(get("/api/auth/my-info").header(HttpHeaders.AUTHORIZATION, bearer(customer.accessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsTopAdministratorAndSystemRoles() throws Exception {
        Account admin = accountWithRole("protected-admin", RoleCode.ADMIN);
        Account staff = accountWithRole("protected-staff", RoleCode.STAFF);
        Account customer = accountWithRole("protected-customer", RoleCode.USER);

        mockMvc.perform(patch(BASE + "/users/{userId}/status", admin.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1042));

        mockMvc.perform(put(BASE + "/users/{userId}/roles", customer.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":["STAFF","USER"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1044));

        mockMvc.perform(delete(BASE + "/roles/STAFF").header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1040));
    }

    @Test
    void rejectsStaffMutatingAnotherStaffBeforeAnyDataIsChanged() throws Exception {
        Account actor = accountWithRole("ownership-staff-actor", RoleCode.STAFF);
        Account target = accountWithRole("ownership-staff-target", RoleCode.STAFF);

        mockMvc.perform(patch(BASE + "/users/{userId}/status", target.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1044));

        mockMvc.perform(put(BASE + "/users/{userId}/roles", target.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":["USER"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1044));

        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM xac_thuc_nguoi_dung WHERE username = ?", String.class, "ownership-staff-target");
        Integer staffRoleCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                          FROM xac_thuc_nguoi_dung_vai_tro user_role
                          JOIN xac_thuc_nguoi_dung user_account ON user_account.id = user_role.user_id
                         WHERE user_account.username = ?
                           AND user_role.role_code = 'STAFF'
                        """, Integer.class, "ownership-staff-target");

        assertThat(storedStatus).isEqualTo("ACTIVE");
        assertThat(staffRoleCount).isOne();
        mockMvc.perform(get("/api/auth/my-info").header(HttpHeaders.AUTHORIZATION, bearer(target.accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    void letsOnlyAdminManageCustomRolesAndValidatesReferences() throws Exception {
        Account admin = accountWithRole("manage-admin", RoleCode.ADMIN);
        Account staff = accountWithRole("manage-staff", RoleCode.STAFF);

        mockMvc.perform(post(BASE + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleJson("OPS", Set.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        mockMvc.perform(post(BASE + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleJson("POWER_USER", Set.of("SYSTEM_ROLE_MANAGE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1055));

        createRole(admin.accessToken(), "OPS", Set.of("SYSTEM_USER_READ"));

        mockMvc.perform(put(BASE + "/roles/OPS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description":"Operations role",
                                  "permissionCodes":["SYSTEM_PERMISSION_READ"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.description").value("Operations role"))
                .andExpect(jsonPath("$.result.permissions[0]").value("SYSTEM_PERMISSION_READ"));

        mockMvc.perform(post(BASE + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleJson("BROKEN", Set.of("MISSING_PERMISSION"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1038));

        mockMvc.perform(delete(BASE + "/roles/OPS").header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk());
        Integer roleCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM xac_thuc_vai_tro WHERE code = 'OPS'", Integer.class);
        assertThat(roleCount).isZero();
    }

    @Test
    void letsAdminLockAStaffAccountAndImmediatelyRevokesItsSession() throws Exception {
        Account admin = accountWithRole("status-admin", RoleCode.ADMIN);
        Account staff = accountWithRole("status-staff", RoleCode.STAFF);

        mockMvc.perform(patch(BASE + "/users/{userId}/status", staff.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("LOCKED"));

        mockMvc.perform(get(BASE + "/users").header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokesSessionsWhenPermissionsOfAnAssignedRoleChange() throws Exception {
        Account admin = accountWithRole("permission-admin", RoleCode.ADMIN);
        Account customer = accountWithRole("permission-customer", RoleCode.USER);
        createRole(admin.accessToken(), "AUDITOR", Set.of("SYSTEM_USER_READ"));

        mockMvc.perform(put(BASE + "/users/{userId}/roles", customer.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":["USER","AUDITOR"]}
                                """))
                .andExpect(status().isOk());

        String auditorAccessToken = authenticate("permission-customer");
        mockMvc.perform(get(BASE + "/users").header(HttpHeaders.AUTHORIZATION, bearer(auditorAccessToken)))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE + "/roles/AUDITOR")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"No longer an auditor","permissionCodes":[]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/users").header(HttpHeaders.AUTHORIZATION, bearer(auditorAccessToken)))
                .andExpect(status().isUnauthorized());
    }

    private Account accountWithRole(String username, RoleCode roleCode) throws Exception {
        UUID userId = register(username);
        if (roleCode != RoleCode.USER) {
            jdbcTemplate.update("""
                    INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code)
                    SELECT id, ? FROM xac_thuc_nguoi_dung WHERE username = ?
                    """, roleCode.name(), username);
            entityManager.clear();
        }
        return new Account(userId, authenticate(username));
    }

    private UUID register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "email":"%s@example.com",
                                  "password":"%s"
                                }
                                """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.result.id"));
    }

    private String authenticate(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.result.accessToken");
    }

    private void createRole(String accessToken, String code, Set<String> permissions) throws Exception {
        mockMvc.perform(post(BASE + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleJson(code, permissions)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.code").value(code));
    }

    private String roleJson(String code, Set<String> permissions) {
        String permissionJson = permissions.stream()
                .sorted()
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {
                  "code":"%s",
                  "description":"Test role",
                  "permissionCodes":[%s]
                }
                """.formatted(code, permissionJson);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Account(UUID id, String accessToken) {}
}
