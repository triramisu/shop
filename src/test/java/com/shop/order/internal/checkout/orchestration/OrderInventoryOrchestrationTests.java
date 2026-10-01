package com.shop.order.internal.checkout.orchestration;

import static com.shop.order.support.OrderTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThatThrownBy(() -> orchestration.getLines().clear()).isInstanceOf(UnsupportedOperationException.class);
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

        assertThatThrownBy(() -> orchestration.completeFailed(createdAt.plusSeconds(4)))
                .isInstanceOf(IllegalStateException.class);
        orchestration.markCompensationRequired(createdAt.plusSeconds(4));
        assertThat(orchestration.getStatus()).isEqualTo(InventoryOrchestrationStatus.COMPENSATION_REQUIRED);
    }
}
