package com.shop.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentStatusChangedEventTests {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void normalizesAValidFailedEvent() {
        PaymentStatusChangedEvent event = event(PaymentStatus.FAILED, null, "declined");

        assertThat(event.amount()).isEqualByComparingTo("10.00");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.providerCode()).isEqualTo("SANDBOX");
        assertThat(event.providerReference()).isNull();
        assertThat(event.failureCode()).isEqualTo("DECLINED");
    }

    @Test
    void rejectsStateSpecificDataThatWouldProduceAnInvalidEvent() {
        assertThatThrownBy(() -> event(PaymentStatus.SUCCEEDED, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider reference");
        assertThatThrownBy(() -> event(PaymentStatus.FAILED, "reference-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failure code");
        assertThatThrownBy(() -> event(PaymentStatus.PENDING, "reference-1", "DECLINED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only valid");
        assertThatThrownBy(() -> new PaymentStatusChangedEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        PaymentStatus.CREATED,
                        PaymentStatus.PENDING,
                        new BigDecimal("10.00"),
                        "USD",
                        "?",
                        "reference-1",
                        null,
                        OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider code");
    }

    private PaymentStatusChangedEvent event(PaymentStatus currentStatus, String providerReference, String failureCode) {
        return new PaymentStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentStatus.CREATED,
                currentStatus,
                new BigDecimal("10.00"),
                "usd",
                "sandbox",
                providerReference,
                failureCode,
                OCCURRED_AT);
    }
}
