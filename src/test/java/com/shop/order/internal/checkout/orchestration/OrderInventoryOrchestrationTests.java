package com.shop.order.internal.checkout.orchestration;

import static com.shop.order.support.OrderTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.event.PaymentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderInventoryOrchestrationTests {

    @Test
    void followsTheSuccessfulReservationStateMachine() {
        Instant createdAt = Instant.parse("2026-10-02T00:00:00Z");
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        var orchestration = OrderInventoryOrchestration.start(
                pendingOrder("owner", createdAt), eventId, correlationId, createdAt.plusSeconds(900), createdAt);
        var line = orchestration.getLines().getFirst();

        assertThat(orchestration.claim(
                        eventId, correlationId, orchestration.getOrder().getId(), createdAt.plusSeconds(1)))
                .isTrue();
        orchestration.markReserved(line.getReservationId(), UUID.randomUUID(), createdAt.plusSeconds(2));
        orchestration.completeReserved(createdAt.plusSeconds(3));

        assertThat(orchestration.getStatus()).isEqualTo(InventoryOrchestrationStatus.RESERVED);
        assertThat(line.getStatus()).isEqualTo(InventoryReservationLineStatus.RESERVED);
        assertThat(orchestration.claim(
                        eventId, correlationId, orchestration.getOrder().getId(), createdAt.plusSeconds(4)))
                .isFalse();
        var readOnlyLines = orchestration.getLines();
        assertThatThrownBy(readOnlyLines::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresCompensationToFinishBeforeTheOrchestrationCanFail() {
        Instant createdAt = Instant.parse("2026-10-02T00:00:00Z");
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        var orchestration = OrderInventoryOrchestration.start(
                pendingOrder("owner", createdAt), eventId, correlationId, createdAt.plusSeconds(900), createdAt);
        var line = orchestration.getLines().getFirst();
        UUID stockItemId = UUID.randomUUID();
        orchestration.claim(eventId, correlationId, orchestration.getOrder().getId(), createdAt.plusSeconds(1));
        orchestration.markReserved(line.getReservationId(), stockItemId, createdAt.plusSeconds(2));
        orchestration.beginCompensation("INVENTORY_INSUFFICIENT_STOCK", createdAt.plusSeconds(3));

        Instant incompleteAt = createdAt.plusSeconds(4);
        assertThatThrownBy(() -> orchestration.completeFailed(incompleteAt)).isInstanceOf(IllegalStateException.class);
        orchestration.markCompensationRequired(createdAt.plusSeconds(4));
        assertThat(orchestration.getStatus()).isEqualTo(InventoryOrchestrationStatus.COMPENSATION_REQUIRED);
    }

    @Test
    void recoversOnlyInterruptedProcessingAndCompensationStates() {
        Instant createdAt = Instant.parse("2026-10-02T00:00:00Z");
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        var orchestration = OrderInventoryOrchestration.start(
                pendingOrder("owner", createdAt), eventId, correlationId, createdAt.plusSeconds(900), createdAt);
        var line = orchestration.getLines().getFirst();
        orchestration.claim(eventId, correlationId, orchestration.getOrder().getId(), createdAt.plusSeconds(1));

        orchestration.markRetryRequired("INTERRUPTED", createdAt.plusSeconds(2));

        assertThat(orchestration.getStatus()).isEqualTo(InventoryOrchestrationStatus.RETRY_REQUIRED);
        assertThat(orchestration.getFailureCode()).isEqualTo("INTERRUPTED");
        orchestration.claim(eventId, correlationId, orchestration.getOrder().getId(), createdAt.plusSeconds(3));
        orchestration.markReserved(line.getReservationId(), UUID.randomUUID(), createdAt.plusSeconds(4));
        orchestration.beginCompensation("FAILED", createdAt.plusSeconds(5));
        orchestration.markCompensationRequired(createdAt.plusSeconds(6));

        orchestration.resumeCompensation(createdAt.plusSeconds(7));

        assertThat(orchestration.getStatus()).isEqualTo(InventoryOrchestrationStatus.COMPENSATING);
        assertThat(orchestration.getUpdatedAt()).isEqualTo(createdAt.plusSeconds(7));
        Instant invalidRetryAt = createdAt.plusSeconds(8);
        assertThatThrownBy(() -> orchestration.markRetryRequired("INTERRUPTED", invalidRetryAt))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmsReservedLinesOnlyAfterASuccessfulPayment() {
        Instant createdAt = Instant.parse("2026-10-02T00:00:00Z");
        var orchestration = reservedOrchestration(createdAt);
        var line = orchestration.getLines().getFirst();
        UUID attemptId = UUID.randomUUID();
        orchestration.assignPaymentAttempt(attemptId, 1, createdAt.plusSeconds(4));

        assertThat(orchestration.beginPaymentConfirmation(
                        PaymentStatus.SUCCEEDED, createdAt.plusSeconds(5), createdAt.plusSeconds(6)))
                .isTrue();
        orchestration.markPaymentConfirmed(line.getReservationId(), createdAt.plusSeconds(7));
        orchestration.completePaymentConfirmation(createdAt.plusSeconds(8));

        assertThat(orchestration.getStatus()).isEqualTo(InventoryOrchestrationStatus.PAYMENT_CONFIRMED);
        assertThat(orchestration.getPaymentAttemptId()).isEqualTo(attemptId);
        assertThat(line.getStatus()).isEqualTo(InventoryReservationLineStatus.CONFIRMED);
        assertThat(orchestration.beginPaymentRelease(
                        PaymentStatus.FAILED, createdAt.plusSeconds(9), createdAt.plusSeconds(10)))
                .isFalse();
    }

    @Test
    void releasesReservedLinesForAFailedPaymentAndIgnoresAnOlderEvent() {
        Instant createdAt = Instant.parse("2026-10-02T00:00:00Z");
        var orchestration = reservedOrchestration(createdAt);
        var line = orchestration.getLines().getFirst();
        orchestration.assignPaymentAttempt(UUID.randomUUID(), 1, createdAt.plusSeconds(4));
        orchestration.recordPaymentPending(PaymentStatus.PENDING, createdAt.plusSeconds(6), createdAt.plusSeconds(6));

        assertThat(orchestration.beginPaymentRelease(
                        PaymentStatus.FAILED, createdAt.plusSeconds(7), createdAt.plusSeconds(8)))
                .isTrue();
        orchestration.markPaymentReleased(line.getReservationId(), createdAt.plusSeconds(9));
        orchestration.completePaymentRelease(createdAt.plusSeconds(10));

        assertThat(orchestration.getStatus()).isEqualTo(InventoryOrchestrationStatus.PAYMENT_RELEASED);
        assertThat(line.getStatus()).isEqualTo(InventoryReservationLineStatus.RELEASED);
        assertThat(orchestration.recordPaymentPending(
                        PaymentStatus.UNKNOWN, createdAt.plusSeconds(5), createdAt.plusSeconds(11)))
                .isFalse();
    }

    private OrderInventoryOrchestration reservedOrchestration(Instant createdAt) {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        var orchestration = OrderInventoryOrchestration.start(
                pendingOrder("owner", createdAt), eventId, correlationId, createdAt.plusSeconds(900), createdAt);
        var line = orchestration.getLines().getFirst();
        orchestration.claim(eventId, correlationId, orchestration.getOrder().getId(), createdAt.plusSeconds(1));
        orchestration.markReserved(line.getReservationId(), UUID.randomUUID(), createdAt.plusSeconds(2));
        orchestration.completeReserved(createdAt.plusSeconds(3));
        return orchestration;
    }
}
