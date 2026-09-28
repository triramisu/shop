package com.shop.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IdentitySchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsIdentityTablesAndSeedsSystemAdministrationRbac() {
        assertThat(rowCount("xac_thuc_nguoi_dung")).isZero();
        assertThat(rowCount("xac_thuc_quyen_han")).isEqualTo(6);
        assertThat(rowCount("xac_thuc_nguoi_dung_vai_tro")).isZero();
        assertThat(rowCount("xac_thuc_vai_tro_quyen_han")).isEqualTo(11);
        assertThat(rowCount("xac_thuc_phien_lam_moi")).isZero();
        assertThat(rowCount("xac_thuc_dang_nhap_that_bai")).isZero();
        assertThat(rowCount("xac_thuc_thu_thach_captcha")).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT lock_name FROM xac_thuc_khoa_captcha ORDER BY lock_name", String.class))
                .containsExactly("challenge-issuance");

        List<String> roles = jdbcTemplate.queryForList("SELECT code FROM xac_thuc_vai_tro ORDER BY code", String.class);
        assertThat(roles).containsExactly("ADMIN", "STAFF", "USER");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code FROM xac_thuc_vai_tro WHERE system_role = TRUE ORDER BY code", String.class))
                .containsExactly("ADMIN", "STAFF", "USER");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT permission_code FROM xac_thuc_vai_tro_quyen_han "
                                + "WHERE role_code = 'ADMIN' ORDER BY permission_code",
                        String.class))
                .containsExactly(
                        "SYSTEM_PERMISSION_READ",
                        "SYSTEM_ROLE_MANAGE",
                        "SYSTEM_ROLE_READ",
                        "SYSTEM_USER_READ",
                        "SYSTEM_USER_ROLE_ASSIGN",
                        "SYSTEM_USER_STATUS_UPDATE");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT description FROM xac_thuc_vai_tro WHERE code = 'STAFF'", String.class))
                .isEqualTo("Nhân viên quản trị");
        assertThat(jdbcTemplate.queryForList("SELECT version FROM xac_thuc_vai_tro", Long.class))
                .containsOnly(0L);
    }

    private long rowCount(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }
}
