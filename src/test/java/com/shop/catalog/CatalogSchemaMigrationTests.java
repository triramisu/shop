package com.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CatalogSchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsEmptyCatalogTablesWithVietnameseModulePrefixes() {
        List<String> catalogTables = jdbcTemplate.queryForList("""
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name LIKE 'san_pham_%'
                 ORDER BY table_name
                """, String.class);

        assertThat(catalogTables).containsExactly("san_pham_bien_the", "san_pham_danh_muc", "san_pham_san_pham");
        assertThat(rowCount("san_pham_danh_muc")).isZero();
        assertThat(rowCount("san_pham_san_pham")).isZero();
        assertThat(rowCount("san_pham_bien_the")).isZero();
    }

    private long rowCount(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }
}
