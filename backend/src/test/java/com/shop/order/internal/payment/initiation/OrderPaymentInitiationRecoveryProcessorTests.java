package com.shop.order.internal.payment.initiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shop.order.internal.payment.configuration.OrderPaymentProperties;
import com.shop.order.internal.payment.initiation.service.OrderPaymentInitiationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderPaymentInitiationRecoveryProcessorTests {

    private OrderPaymentInitiationService initiationService;
    private SimpleMeterRegistry meterRegistry;
    private OrderPaymentInitiationRecoveryProcessor processor;

    @BeforeEach
    void setUp() {
        initiationService = mock(OrderPaymentInitiationService.class);
        meterRegistry = new SimpleMeterRegistry();
        processor = new OrderPaymentInitiationRecoveryProcessor(
                initiationService, new OrderPaymentProperties(), meterRegistry);
    }

    @Test
    void isolatesCandidatesAndRecordsFiniteOutcomeMetrics() {
        Instant now = Instant.parse("2026-10-05T12:00:00Z");
        Instant staleBefore = now.minusSeconds(30);
        UUID initiatedId = UUID.randomUUID();
        UUID skippedId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        when(initiationService.findRecoveryCandidates(staleBefore, 50))
                .thenReturn(List.of(initiatedId, skippedId, failedId));
        when(initiationService.initiate(initiatedId)).thenReturn(true);
        when(initiationService.initiate(skippedId)).thenReturn(false);
        when(initiationService.initiate(failedId)).thenThrow(new IllegalStateException("provider unavailable"));

        var result = processor.reconcileBatch(now);

        assertThat(result).isEqualTo(new OrderPaymentInitiationRecoveryResult(3, 1, 1));
        assertMetric("initiated", 1);
        assertMetric("skipped", 1);
        assertMetric("failed", 1);
    }

    private void assertMetric(String outcome, double expected) {
        assertThat(meterRegistry
                        .get(OrderPaymentInitiationRecoveryProcessor.ITEM_METRIC)
                        .tag("outcome", outcome)
                        .counter()
                        .count())
                .isEqualTo(expected);
    }
}
