package com.shop.order.internal.payment.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderPaymentEventRecoveryJobTests {

    private OrderPaymentEventRecoveryProcessor processor;
    private SimpleMeterRegistry meterRegistry;
    private OrderPaymentEventRecoveryJob job;

    @BeforeEach
    void setUp() {
        processor = mock(OrderPaymentEventRecoveryProcessor.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new OrderPaymentEventRecoveryJob(processor, meterRegistry);
    }

    @Test
    void containsSchedulerFailuresAndRecordsEachRun() {
        when(processor.reconcileBatch(any()))
                .thenReturn(new OrderPaymentEventRecoveryResult(1, 1, 0))
                .thenThrow(new IllegalStateException("query failed"));

        job.reconcileStaleEvents();
        job.reconcileStaleEvents();

        assertRunMetric("success", 1);
        assertRunMetric("failed", 1);
    }

    private void assertRunMetric(String outcome, double expected) {
        assertThat(meterRegistry
                        .get(OrderPaymentEventRecoveryJob.RUN_METRIC)
                        .tag("outcome", outcome)
                        .counter()
                        .count())
                .isEqualTo(expected);
    }
}
