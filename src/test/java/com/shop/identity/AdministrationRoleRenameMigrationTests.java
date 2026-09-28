package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AdministrationRoleRenameMigrationTests {

    @Test
    void preservesAssignmentsAndRevokesOldRoleClaimsWhenRenamingRoles() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:role-rename;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("6"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        byte[] adminId = HexFormat.of().parseHex("00112233445566778899aabbccddeeff");
        byte[] staffId = HexFormat.of().parseHex("ffeeddccbbaa99887766554433221100");
        insertUser(jdbcTemplate, adminId, "legacy-root");
        insertUser(jdbcTemplate, staffId, "legacy-admin");
        jdbcTemplate.update(
                "INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code) VALUES (?, 'SUPER_ADMIN')", adminId);
        jdbcTemplate.update(
                "INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code) VALUES (?, 'ADMIN')", staffId);
        jdbcTemplate.update(
                """
                INSERT INTO xac_thuc_phien_lam_moi
                    (jti, user_id, family_id, token_hash, expires_at, created_at)
                VALUES
                    ('legacy-session', ?, 'legacy-family', ?, ?, ?)
                """,
                adminId,
                "a".repeat(64),
                Timestamp.from(Instant.now().plusSeconds(3600)),
                Timestamp.from(Instant.now()));

        Flyway.configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("7"))
                .load()
                .migrate();

        assertThat(roleOf(jdbcTemplate, adminId)).isEqualTo("ADMIN");
        assertThat(roleOf(jdbcTemplate, staffId)).isEqualTo("STAFF");
        assertThat(jdbcTemplate.queryForList("SELECT code FROM xac_thuc_vai_tro ORDER BY code", String.class))
                .containsExactly("ADMIN", "STAFF", "USER");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT revoked_at FROM xac_thuc_phien_lam_moi WHERE jti = 'legacy-session'", Timestamp.class))
                .isNotNull();
    }

    private void insertUser(JdbcTemplate jdbcTemplate, byte[] id, String username) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO xac_thuc_nguoi_dung
                    (id, username, email, password_hash, status, email_verified, version, created_at, updated_at)
                VALUES
                    (?, ?, ?, ?, 'ACTIVE', FALSE, 0, ?, ?)
                """, id, username, username + "@example.com", "hash", Timestamp.from(now), Timestamp.from(now));
    }

    private String roleOf(JdbcTemplate jdbcTemplate, byte[] userId) {
        return jdbcTemplate.queryForObject(
                "SELECT role_code FROM xac_thuc_nguoi_dung_vai_tro WHERE user_id = ?", String.class, userId);
    }
}
