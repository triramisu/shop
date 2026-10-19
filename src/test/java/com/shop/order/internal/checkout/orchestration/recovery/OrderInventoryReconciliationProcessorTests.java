package com.shop.order.internal.checkout.orchestration.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.order.internal.checkout.configuration.CheckoutInventoryProperties;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationCoordinator;
import com.shop.order.internal.checkout.service.OrderInventoryOrchestrationStateService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderInventoryReconciliationProcessorTests {

    private OrderInventoryOrchestrationStateService stateService;
    private OrderInventoryReservationCoordinator coordinator;
    private SimpleMeterRegistry meterRegistry;
    private OrderInventoryReconciliationProcessor processor;

    @BeforeEach
    void setUp() {
        stateService = mock(OrderInventoryOrchestrationStateService.class);
        coordinator = mock(OrderInventoryReservationCoordinator.class);
        meterRegistry = new SimpleMeterRegistry();
        processor = new OrderInventoryReconciliationProcessor(
                stateService, coordinator, new CheckoutInventoryProperties(), meterRegistry);
    }

    @Test
    void isolatesEachCandidateAndRecordsFiniteOutcomeMetrics() {
        Instant now = Instant.parse("2026-10-02T12:00:00Z");
        Instant staleBefore = now.minusSeconds(30);
        UUID recoveredId = UUID.randomUUID();
        UUID skippedId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        OrderInventoryRecoveryPlan plan = mock(OrderInventoryRecoveryPlan.class);
        when(stateService.findRecoveryCandidates(staleBefore, 50))
                .thenReturn(List.of(recoveredId, skippedId, failedId));
        when(stateService.prepareRecovery(recoveredId, staleBefore, now)).thenReturn(Optional.of(plan));
        when(stateService.prepareRecovery(skippedId, staleBefore, now)).thenReturn(Optional.empty());
        when(stateService.prepareRecovery(failedId, staleBefore, now))
                .thenThrow(new IllegalStateException("database unavailable"));

        var result = processor.reconcileBatch(now);

        assertThat(result).isEqualTo(new OrderInventoryReconciliationProcessor.ReconciliationBatchResult(3, 1, 1, 1));
        verify(coordinator).recover(plan);
        assertMetric("recovered", 1);
        assertMetric("skipped", 1);
        assertMetric("failed", 1);
    }

    private void assertMetric(String outcome, double expected) {
        assertThat(meterRegistry
                        .get(OrderInventoryReconciliationProcessor.ITEM_METRIC)
                        .tag("outcome", outcome)
                        .counter()
                        .count())
                .isEqualTo(expected);
    }
}
