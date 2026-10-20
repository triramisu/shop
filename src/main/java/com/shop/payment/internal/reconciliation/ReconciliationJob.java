package com.shop.payment.internal.reconciliation;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;

final class ReconciliationJob {
    private final ReconciliationProcessor processor;
    private final MeterRegistry meters;

    ReconciliationJob(ReconciliationProcessor processor, MeterRegistry meters) {
        this.processor = processor;
        this.meters = meters;
    }

    @Scheduled(
            fixedDelayString = "${app.payment.reconciliation.fixed-delay:10s}",
            initialDelayString = "${app.payment.reconciliation.initial-delay:30s}")
    void run() {
        try {
            processor.runBatch();
            meters.counter("shop.payment.reconciliation.runs", "outcome", "success")
                    .increment();
        } catch (RuntimeException error) {
            meters.counter("shop.payment.reconciliation.runs", "outcome", "failed")
                    .increment();
        }
    }
}
