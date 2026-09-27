package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.identity.internal.administration.service.SuperAdminBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:shop-super-admin;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "app.identity.super-admin.bootstrap-enabled=true",
            "app.identity.super-admin.username=bootstrap-root",
            "app.identity.super-admin.email=bootstrap-root@example.com",
            "app.identity.super-admin.password=BootstrapStr0ngPassword!"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuperAdminBootstrapIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SuperAdminBootstrapService superAdminBootstrapService;

    @Test
    void createsExactlyOneConfiguredTopAdministratorWithoutAStoredPlaintextPassword() throws Exception {
        superAdminBootstrapService.run(new DefaultApplicationArguments(new String[0]));
        Integer superAdminCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM xac_thuc_nguoi_dung shop_user
                  JOIN xac_thuc_nguoi_dung_vai_tro user_role ON user_role.user_id = shop_user.id
                 WHERE user_role.role_code = 'SUPER_ADMIN'
                """, Integer.class);
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM xac_thuc_nguoi_dung WHERE username = 'bootstrap-root'", String.class);

        assertThat(superAdminCount).isEqualTo(1);
        assertThat(passwordHash).startsWith("$2").doesNotContain("BootstrapStr0ngPassword!");

        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"bootstrap-root",
                                  "password":"BootstrapStr0ngPassword!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty());
    }
}
