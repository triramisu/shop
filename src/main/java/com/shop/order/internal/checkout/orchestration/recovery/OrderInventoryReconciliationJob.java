package com.shop.order.internal.checkout.orchestration.recovery;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "app.order.checkout.inventory.reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class OrderInventoryReconciliationJob {

    static final String RUN_METRIC = "shop.order.checkout.inventory.reconciliation.runs";

    OrderInventoryReconciliationProcessor processor;
    MeterRegistry meterRegistry;

    @Scheduled(
            fixedDelayString = "${app.order.checkout.inventory.reconciliation.fixed-delay:30s}",
            initialDelayString = "${app.order.checkout.inventory.reconciliation.initial-delay:30s}")
    void reconcileStaleOrchestrations() {
        try {
            var result = processor.reconcileBatch(Instant.now());
            meterRegistry.counter(RUN_METRIC, "outcome", "success").increment();
            if (result.selected() > 0) {
                log.info(
                        "Reconciled order inventory batch: selected={}, recovered={}, skipped={}, failed={}",
                        result.selected(),
                        result.recovered(),
                        result.skipped(),
                        result.failed());
            }
        } catch (RuntimeException exception) {
            meterRegistry.counter(RUN_METRIC, "outcome", "failed").increment();
            log.error("Order inventory reconciliation batch failed", exception);
        }
    }
}
