package com.shop.payment.internal.refund.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class RefundMigrationFixtures {
    static final UUID OLD_PAYMENT = UUID.fromString("00000000-0000-0000-0000-000000000025");
    private static final Instant AT = Instant.parse("2026-10-15T00:00:00Z");

    static void seedV24(JdbcTemplate jdbc) {
        jdbc.update(
                "INSERT INTO thanh_toan_lan_thu (id, order_id, attempt_number, amount, currency, provider_code, "
                        + "provider_reference, status, completed_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, 1, 100.00, 'USD', 'FAKE', 'old_payment_reference', 'SUCCEEDED', ?, 0, ?, ?)",
                bytes(OLD_PAYMENT),
                bytes(UUID.randomUUID()),
                Timestamp.from(AT),
                Timestamp.from(AT),
                Timestamp.from(AT));
        for (String role : new String[] {"ADMIN", "USER"}) {
            UUID userId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO xac_thuc_nguoi_dung (id, username, email, password_hash, status, "
                            + "email_verified, version, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', FALSE, 0, ?, ?)",
                    bytes(userId),
                    "upgrade-" + role,
                    "upgrade-" + role + "@example.com",
                    "unusable-migration-fixture",
                    Timestamp.from(AT),
                    Timestamp.from(AT));
            jdbc.update(
                    "INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code) VALUES (?, ?)", bytes(userId), role);
            jdbc.update(
                    "INSERT INTO xac_thuc_phien_lam_moi (jti, user_id, family_id, token_hash, expires_at, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(),
                    bytes(userId),
                    UUID.randomUUID().toString(),
                    (role.equals("ADMIN") ? "a" : "b").repeat(64),
                    Timestamp.from(AT.plusSeconds(86400 * 365)),
                    Timestamp.from(AT));
        }
    }

    static void assertUpgraded(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForMap(
                        "SELECT amount, currency, provider_reference, status, version FROM thanh_toan_lan_thu WHERE id=?",
                        bytes(OLD_PAYMENT)))
                .containsEntry("currency", "USD")
                .containsEntry("provider_reference", "old_payment_reference")
                .containsEntry("status", "SUCCEEDED");
        assertThat(jdbc.queryForObject(
                        "SELECT amount FROM thanh_toan_lan_thu WHERE id=?",
                        java.math.BigDecimal.class,
                        bytes(OLD_PAYMENT)))
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject(
                        "SELECT version FROM thanh_toan_lan_thu WHERE id=?", Long.class, bytes(OLD_PAYMENT)))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM xac_thuc_vai_tro_quyen_han WHERE permission_code='PAYMENT_REFUND' AND role_code='ADMIN'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM xac_thuc_vai_tro_quyen_han WHERE permission_code='PAYMENT_REFUND' AND role_code IN ('USER', 'STAFF')",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM xac_thuc_phien_lam_moi WHERE token_hash=? AND revoked_at IS NOT NULL",
                        Integer.class,
                        "a".repeat(64)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM xac_thuc_phien_lam_moi WHERE token_hash=? AND revoked_at IS NULL",
                        Integer.class,
                        "b".repeat(64)))
                .isEqualTo(1);
    }

    static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private RefundMigrationFixtures() {}
}
