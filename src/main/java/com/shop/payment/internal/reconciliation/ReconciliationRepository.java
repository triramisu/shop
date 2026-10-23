package com.shop.payment.internal.reconciliation;

import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.provider.PaymentStatusQueryResult;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlValue;

/** Payment-owned JDBC metadata; journal deliberately exposes no update/delete operation. */
final class ReconciliationRepository {
    private final JdbcTemplate jdbc;

    ReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Instant lockProvider() {
        return jdbc.queryForObject(
                "SELECT next_claim_at FROM thanh_toan_nhip_doi_soat WHERE provider_code='VNPAY' FOR UPDATE",
                (rs, row) -> instant(rs, 1));
    }

    void pace(Instant next) {
        jdbc.update("UPDATE thanh_toan_nhip_doi_soat SET next_claim_at=? WHERE provider_code='VNPAY'", time(next));
    }

    Optional<UUID> candidate(Instant now, Instant staleBefore) {
        var due = jdbc.query("""
                SELECT payment_attempt_id FROM thanh_toan_doi_soat
                WHERE (state='READY' AND next_query_at<=?) OR (state='LEASED' AND lease_until<=?)
                ORDER BY next_query_at, payment_attempt_id LIMIT 1
                """, (rs, row) -> uuid(rs.getBytes(1)), time(now), time(now));
        if (!due.isEmpty()) return Optional.of(due.getFirst());
        return jdbc.query("""
                SELECT p.id FROM thanh_toan_lan_thu p
                WHERE p.provider_code='VNPAY' AND p.status IN ('REQUIRES_ACTION','PENDING','UNKNOWN')
                AND p.created_at<=? AND NOT EXISTS
                    (SELECT 1 FROM thanh_toan_doi_soat c WHERE c.payment_attempt_id=p.id)
                ORDER BY p.created_at, p.id LIMIT 1
                """, (rs, row) -> uuid(rs.getBytes(1)), time(staleBefore)).stream()
                .findFirst();
    }

    void seed(PaymentAttempt attempt, Instant now, Instant deadline) {
        jdbc.update(
                """
                INSERT INTO thanh_toan_doi_soat
                  (payment_attempt_id,state,query_count,next_query_at,deadline_at,created_at,updated_at)
                SELECT ?, 'READY', 0, ?, ?, ?, ? WHERE NOT EXISTS
                  (SELECT 1 FROM thanh_toan_doi_soat WHERE payment_attempt_id=?)
                """, bytes(attempt.getId()), time(now), time(deadline), time(now), time(now), bytes(attempt.getId()));
    }

    Row lock(UUID id) {
        return jdbc.queryForObject(
                "SELECT * FROM thanh_toan_doi_soat WHERE payment_attempt_id=? FOR UPDATE",
                (rs, number) -> new Row(
                        id,
                        rs.getString("state"),
                        rs.getInt("query_count"),
                        instant(rs, "next_query_at"),
                        instant(rs, "deadline_at"),
                        uuid(rs.getBytes("lease_token")),
                        instant(rs, "lease_started_at"),
                        instant(rs, "lease_until"),
                        rs.getObject("payment_version", Long.class)),
                bytes(id));
    }

    void claim(ReconciliationLease lease, Instant now) {
        jdbc.update(
                """
                UPDATE thanh_toan_doi_soat SET state='LEASED',query_count=?,lease_token=?,
                lease_started_at=?,lease_until=?,payment_version=?,last_error=NULL,updated_at=?
                WHERE payment_attempt_id=?
                """,
                lease.queryCount(),
                bytes(lease.token()),
                time(lease.startedAt()),
                time(lease.until()),
                lease.paymentVersion(),
                time(now),
                bytes(lease.request().paymentAttemptId()));
    }

    void finish(UUID id, String state, String error, Instant next, Instant now) {
        jdbc.update("""
                UPDATE thanh_toan_doi_soat SET state=?,last_error=?,next_query_at=?,updated_at=?,
                lease_token=NULL,lease_started_at=NULL,lease_until=NULL,payment_version=NULL
                WHERE payment_attempt_id=?
                """, state, error, time(next), time(now), bytes(id));
    }

    boolean seen(String hash) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM thanh_toan_lich_su_doi_soat WHERE provider_code='VNPAY' AND receipt_hash=?",
                        Integer.class,
                        hash)
                > 0;
    }

    void append(
            UUID id,
            UUID token,
            int count,
            String outcome,
            String error,
            PaymentStatusQueryResult result,
            boolean newReceipt,
            UUID eventId,
            Instant startedAt,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO thanh_toan_lich_su_doi_soat
                (id,payment_attempt_id,lease_token,query_count,outcome,error_code,provider_code,
                provider_response_id,provider_transaction_no,payload_hash,receipt_hash,event_id,query_started_at,observed_at,created_at)
                VALUES (?,?,?,?,?,?,'VNPAY',?,?,?,?,?,?,?,?)
                """,
                bytes(UUID.randomUUID()),
                bytes(id),
                bytes(token),
                count,
                outcome,
                error,
                result == null ? null : result.providerResponseId(),
                result == null ? null : result.providerTransactionNo(),
                result == null ? null : result.receiptHash(),
                result != null && newReceipt ? result.receiptHash() : null,
                bytes(eventId),
                time(startedAt),
                result == null ? null : time(result.observedAt()),
                time(now));
    }

    long manualCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM thanh_toan_doi_soat c JOIN thanh_toan_lan_thu p ON p.id=c.payment_attempt_id
                WHERE c.state='MANUAL' AND p.status IN ('REQUIRES_ACTION','PENDING','UNKNOWN')
                """, Long.class);
    }

    Instant oldestPending() {
        return jdbc.queryForObject("""
                SELECT MIN(p.created_at) FROM thanh_toan_lan_thu p
                WHERE p.provider_code='VNPAY' AND p.status IN ('REQUIRES_ACTION','PENDING','UNKNOWN')
                """, (rs, number) -> instant(rs, 1));
    }

    static byte[] bytes(UUID id) {
        return id == null
                ? null
                : ByteBuffer.allocate(16)
                        .putLong(id.getMostSignificantBits())
                        .putLong(id.getLeastSignificantBits())
                        .array();
    }

    static UUID uuid(byte[] bytes) {
        if (bytes == null) return null;
        var buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name, utcCalendar());
        return value == null ? null : value.toInstant();
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp value = rs.getTimestamp(column, utcCalendar());
        return value == null ? null : value.toInstant();
    }

    static SqlValue time(Instant instant) {
        // Match Hibernate's UTC calendar, independent of the host/driver default timezone.
        return new SqlValue() {
            @Override
            public void setValue(PreparedStatement statement, int parameter) throws SQLException {
                statement.setTimestamp(parameter, Timestamp.from(instant), utcCalendar());
            }

            @Override
            public void cleanup() {}
        };
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    record Row(
            UUID id,
            String state,
            int count,
            Instant nextQueryAt,
            Instant deadline,
            UUID token,
            Instant started,
            Instant until,
            Long paymentVersion) {}
}
