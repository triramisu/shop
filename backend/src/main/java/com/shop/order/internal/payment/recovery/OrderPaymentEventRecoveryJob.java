package com.shop.order.internal.payment.recovery;

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
        prefix = "app.order.payment.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class OrderPaymentEventRecoveryJob {

    static final String RUN_METRIC = "shop.order.payment.recovery.runs";

    OrderPaymentEventRecoveryProcessor processor;
    MeterRegistry meterRegistry;

    @Scheduled(
            fixedDelayString = "${app.order.payment.recovery.fixed-delay:30s}",
            initialDelayString = "${app.order.payment.recovery.initial-delay:30s}")
    void reconcileStaleEvents() {
        try {
            var result = processor.reconcileBatch(Instant.now());
            meterRegistry.counter(RUN_METRIC, "outcome", "success").increment();
            if (result.selected() > 0) {
                log.info(
                        "Reconciled order payment events: selected={}, recovered={}, failed={}",
                        result.selected(),
                        result.recovered(),
                        result.failed());
            }
        } catch (RuntimeException exception) {
            meterRegistry.counter(RUN_METRIC, "outcome", "failed").increment();
            log.error("Order payment event recovery batch failed", exception);
        }
    }
}
