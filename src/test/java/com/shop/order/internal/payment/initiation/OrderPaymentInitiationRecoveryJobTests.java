package com.shop.order.internal.payment.initiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderPaymentInitiationRecoveryJobTests {

    private OrderPaymentInitiationRecoveryProcessor processor;
    private SimpleMeterRegistry meterRegistry;
    private OrderPaymentInitiationRecoveryJob job;

    @BeforeEach
    void setUp() {
        processor = mock(OrderPaymentInitiationRecoveryProcessor.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new OrderPaymentInitiationRecoveryJob(processor, meterRegistry);
    }

    @Test
    void containsSchedulerFailuresAndRecordsEachRun() {
        when(processor.reconcileBatch(any()))
                .thenReturn(new OrderPaymentInitiationRecoveryResult(1, 1, 0))
                .thenThrow(new IllegalStateException("query failed"));

        job.retryStalePaymentInitiations();
        job.retryStalePaymentInitiations();

        assertRunMetric("success", 1);
        assertRunMetric("failed", 1);
    }

    private void assertRunMetric(String outcome, double expected) {
        assertThat(meterRegistry
                        .get(OrderPaymentInitiationRecoveryJob.RUN_METRIC)
                        .tag("outcome", outcome)
                        .counter()
                        .count())
                .isEqualTo(expected);
    }
}
