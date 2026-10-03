package com.shop.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaymentSchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsVietnamesePaymentTableWithDomainConstraintsAndIndexes() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_name = 'thanh_toan_lan_thu'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(constraints("thanh_toan_lan_thu"))
                .contains(
                        "uk_thanh_toan_lan_thu_order_attempt",
                        "uk_thanh_toan_lan_thu_provider_reference",
                        "ck_thanh_toan_lan_thu_attempt",
                        "ck_thanh_toan_lan_thu_amount",
                        "ck_thanh_toan_lan_thu_currency",
                        "ck_thanh_toan_lan_thu_provider_code",
                        "ck_thanh_toan_lan_thu_provider_reference",
                        "ck_thanh_toan_lan_thu_failure_code",
                        "ck_thanh_toan_lan_thu_status",
                        "ck_thanh_toan_lan_thu_state",
                        "ck_thanh_toan_lan_thu_audit_time");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT LOWER(index_name) FROM information_schema.indexes "
                                + "WHERE table_schema = 'public' AND table_name = 'thanh_toan_lan_thu'",
                        String.class))
                .contains("idx_thanh_toan_lan_thu_order_created", "idx_thanh_toan_lan_thu_status_updated");
    }

    @Test
    void doesNotCreateACrossModuleForeignKeyToOrder() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.table_constraints "
                                + "WHERE table_schema = 'public' AND table_name = 'thanh_toan_lan_thu' "
                                + "AND constraint_type = 'FOREIGN KEY'",
                        Integer.class))
                .isZero();
    }

    @Test
    void addsHostedCheckoutUrlWithoutAddingSensitiveCardColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT LOWER(column_name) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'thanh_toan_lan_thu'",
                String.class);

        assertThat(columns).contains("action_url");
        assertThat(columns)
                .noneMatch(column -> column.contains("pan")
                        || column.contains("card_number")
                        || column.contains("cvv")
                        || column.contains("cvc"));
    }

    private List<String> constraints(String table) {
        return jdbcTemplate.queryForList(
                "SELECT LOWER(constraint_name) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND table_name = ?",
                String.class,
                table);
    }
}
