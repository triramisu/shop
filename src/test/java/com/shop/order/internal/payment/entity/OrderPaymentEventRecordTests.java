package com.shop.order.internal.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.event.PaymentStatusChangedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPaymentEventRecordTests {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final String PAYLOAD_HASH = "a".repeat(64);

    @Test
    void storesAndReconstructsAVersionedPaymentEvent() {
        PaymentStatusChangedEvent event = successfulEvent();
        OrderPaymentEventRecord record =
                OrderPaymentEventRecord.receive(event, PAYLOAD_HASH, OCCURRED_AT.plusSeconds(1));

        assertThat(record.getOutcome()).isEqualTo(OrderPaymentEventOutcome.RECEIVED);
        assertThat(record.claim()).isTrue();
        record.complete(OrderPaymentEventOutcome.COMPLETED, null, OCCURRED_AT.plusSeconds(2));

        assertThat(record.getOutcome()).isEqualTo(OrderPaymentEventOutcome.COMPLETED);
        assertThat(record.getProcessedAt()).isEqualTo(OCCURRED_AT.plusSeconds(2));
        assertThat(record.toEvent()).isEqualTo(event);
        assertThat(record.claim()).isFalse();
    }

    @Test
    void onlyCompletesAClaimedEventWithAnAllowedOutcome() {
        OrderPaymentEventRecord record =
                OrderPaymentEventRecord.receive(successfulEvent(), PAYLOAD_HASH, OCCURRED_AT.plusSeconds(1));

        assertThatThrownBy(() -> record.complete(OrderPaymentEventOutcome.COMPLETED, null, OCCURRED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        record.claim();
        assertThatThrownBy(() -> record.complete(OrderPaymentEventOutcome.PROCESSING, null, OCCURRED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PaymentStatusChangedEvent successfulEvent() {
        return new PaymentStatusChangedEvent(
                PaymentStatusChangedEvent.CURRENT_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentStatus.PENDING,
                PaymentStatus.SUCCEEDED,
                new BigDecimal("25.00"),
                "USD",
                "FAKE",
                "fake-reference",
                null,
                OCCURRED_AT);
    }
}
