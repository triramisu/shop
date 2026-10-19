package com.shop.order.internal.checkout.orchestration.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderInventoryReconciliationJobTests {

    private OrderInventoryReconciliationProcessor processor;
    private SimpleMeterRegistry meterRegistry;
    private OrderInventoryReconciliationJob job;

    @BeforeEach
    void setUp() {
        processor = mock(OrderInventoryReconciliationProcessor.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new OrderInventoryReconciliationJob(processor, meterRegistry);
    }

    @Test
    void containsSchedulerFailuresAndRecordsEachRun() {
        when(processor.reconcileBatch(any()))
                .thenReturn(new OrderInventoryReconciliationProcessor.ReconciliationBatchResult(1, 1, 0, 0))
                .thenThrow(new IllegalStateException("query failed"));

        job.reconcileStaleOrchestrations();
        job.reconcileStaleOrchestrations();

        assertRunMetric("success", 1);
        assertRunMetric("failed", 1);
    }

    private void assertRunMetric(String outcome, double expected) {
        assertThat(meterRegistry
                        .get(OrderInventoryReconciliationJob.RUN_METRIC)
                        .tag("outcome", outcome)
                        .counter()
                        .count())
                .isEqualTo(expected);
    }
}
