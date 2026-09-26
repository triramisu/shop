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
    void createsIdentityTablesAndSeedsBaseRoles() {
        assertThat(rowCount("xac_thuc_nguoi_dung")).isZero();
        assertThat(rowCount("xac_thuc_quyen_han")).isZero();
        assertThat(rowCount("xac_thuc_nguoi_dung_vai_tro")).isZero();
        assertThat(rowCount("xac_thuc_vai_tro_quyen_han")).isZero();
        assertThat(rowCount("xac_thuc_phien_lam_moi")).isZero();
        assertThat(rowCount("xac_thuc_dang_nhap_that_bai")).isZero();
        assertThat(rowCount("xac_thuc_thu_thach_captcha")).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT lock_name FROM xac_thuc_khoa_captcha ORDER BY lock_name", String.class))
                .containsExactly("challenge-issuance");

        List<String> roles = jdbcTemplate.queryForList("SELECT code FROM xac_thuc_vai_tro ORDER BY code", String.class);
        assertThat(roles).containsExactly("ADMIN", "USER");
    }

    private long rowCount(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }
}
