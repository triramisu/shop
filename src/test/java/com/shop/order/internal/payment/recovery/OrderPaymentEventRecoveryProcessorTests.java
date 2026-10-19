package com.shop.order.internal.payment.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shop.order.internal.payment.configuration.OrderPaymentProperties;
import com.shop.order.internal.payment.service.OrderPaymentEventCoordinator;
import com.shop.order.internal.payment.service.OrderPaymentEventInboxService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderPaymentEventRecoveryProcessorTests {

    private OrderPaymentEventInboxService inboxService;
    private OrderPaymentEventCoordinator coordinator;
    private SimpleMeterRegistry meterRegistry;
    private OrderPaymentEventRecoveryProcessor processor;

    @BeforeEach
    void setUp() {
        inboxService = mock(OrderPaymentEventInboxService.class);
        coordinator = mock(OrderPaymentEventCoordinator.class);
        meterRegistry = new SimpleMeterRegistry();
        processor = new OrderPaymentEventRecoveryProcessor(
                inboxService, coordinator, new OrderPaymentProperties(), meterRegistry);
    }

    @Test
    void isolatesCandidatesAndRecordsFiniteOutcomeMetrics() {
        Instant now = Instant.parse("2026-10-05T12:00:00Z");
        Instant staleBefore = now.minusSeconds(30);
        UUID recoveredId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        when(inboxService.findRecoveryCandidates(staleBefore, 50)).thenReturn(List.of(recoveredId, failedId));
        doThrow(new IllegalStateException("database unavailable"))
                .when(coordinator)
                .recover(failedId);

        var result = processor.reconcileBatch(now);

        assertThat(result).isEqualTo(new OrderPaymentEventRecoveryResult(2, 1, 1));
        assertMetric("processed", 1);
        assertMetric("failed", 1);
    }

    private void assertMetric(String outcome, double expected) {
        assertThat(meterRegistry
                        .get(OrderPaymentEventRecoveryProcessor.ITEM_METRIC)
                        .tag("outcome", outcome)
                        .counter()
                        .count())
                .isEqualTo(expected);
    }
}
