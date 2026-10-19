package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockReservationExpirationJobTests {

    @Mock
    private StockReservationExpirationProcessor processor;

    private SimpleMeterRegistry meterRegistry;
    private StockReservationExpirationJob job;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        job = new StockReservationExpirationJob(processor, meterRegistry);
    }

    @Test
    void recordsSuccessfulAndFailedRunsWithoutEscapingSchedulerBoundary() {
        when(processor.expireBatch(any()))
                .thenReturn(new StockReservationExpirationProcessor.ExpirationBatchResult(1, 1, 0, 0))
                .thenThrow(new IllegalStateException("query failed"));

        job.expireDueReservations();
        job.expireDueReservations();

        assertThat(meterRegistry
                        .get(StockReservationExpirationJob.RUN_METRIC)
                        .tag("outcome", "success")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(meterRegistry
                        .get(StockReservationExpirationJob.RUN_METRIC)
                        .tag("outcome", "failed")
                        .counter()
                        .count())
                .isEqualTo(1);
    }
}
