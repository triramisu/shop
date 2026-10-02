package com.shop.order.internal.checkout.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutIdempotencyRecordTests {

    private static final String OWNER = "idempotency-owner";
    private static final String KEY_HASH = "a".repeat(64);
    private static final String FIRST_FINGERPRINT = "b".repeat(64);
    private static final String SECOND_FINGERPRINT = "c".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-02T01:00:00Z");

    @Test
    void completesAndFailsOnlyTheActiveExecution() {
        CheckoutIdempotencyRecord completed = newRecord();
        UUID orderId = UUID.randomUUID();

        completed.complete(completed.getExecutionId(), orderId, NOW.plusSeconds(1));

        assertThat(completed.getStatus()).isEqualTo(CheckoutIdempotencyStatus.COMPLETED);
        assertThat(completed.getResultOrderId()).isEqualTo(orderId);
        assertThat(completed.getFailureCode()).isNull();
        assertThatThrownBy(() -> completed.complete(completed.getExecutionId(), orderId, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);

        CheckoutIdempotencyRecord failed = newRecord();
        failed.fail(failed.getExecutionId(), ErrorCode.CHECKOUT_CART_EMPTY, NOW.plusSeconds(1));

        assertThat(failed.getStatus()).isEqualTo(CheckoutIdempotencyStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo(ErrorCode.CHECKOUT_CART_EMPTY.name());
        assertThat(failed.getResultOrderId()).isNull();
        assertThatThrownBy(() -> failed.fail(UUID.randomUUID(), ErrorCode.CHECKOUT_CART_EMPTY, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restartsOnlyAfterExpirationAndFencesThePreviousExecution() {
        CheckoutIdempotencyRecord record = newRecord();
        UUID previousExecution = record.getExecutionId();

        assertThatThrownBy(() ->
                        record.restart(SECOND_FINGERPRINT, NOW.plus(2, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.HOURS)))
                .isInstanceOf(IllegalStateException.class);

        Instant restartTime = NOW.plus(1, ChronoUnit.DAYS);
        record.fail(record.getExecutionId(), ErrorCode.CHECKOUT_CART_EMPTY, NOW.plusSeconds(1));
        record.restart(SECOND_FINGERPRINT, restartTime.plus(1, ChronoUnit.DAYS), restartTime);

        assertThat(record.getStatus()).isEqualTo(CheckoutIdempotencyStatus.PROCESSING);
        assertThat(record.getExecutionId()).isNotEqualTo(previousExecution);
        assertThat(record.getRequestFingerprint()).isEqualTo(SECOND_FINGERPRINT);
        assertThat(record.getResultOrderId()).isNull();
        assertThat(record.getFailureCode()).isNull();
        assertThatThrownBy(() -> record.complete(previousExecution, UUID.randomUUID(), restartTime.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesHashesAndExpiration() {
        assertThatThrownBy(() -> CheckoutIdempotencyRecord.start(
                        OWNER,
                        CheckoutIdempotencyOperation.CREATE_ORDER,
                        "short",
                        FIRST_FINGERPRINT,
                        NOW.plusSeconds(1),
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckoutIdempotencyRecord.start(
                        OWNER, CheckoutIdempotencyOperation.CREATE_ORDER, KEY_HASH, FIRST_FINGERPRINT, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CheckoutIdempotencyRecord newRecord() {
        return CheckoutIdempotencyRecord.start(
                OWNER,
                CheckoutIdempotencyOperation.CREATE_ORDER,
                KEY_HASH,
                FIRST_FINGERPRINT,
                NOW.plus(1, ChronoUnit.DAYS),
                NOW);
    }
}
