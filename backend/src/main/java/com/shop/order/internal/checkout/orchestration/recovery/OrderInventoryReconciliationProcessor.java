package com.shop.order.internal.checkout.orchestration.recovery;

import com.shop.order.internal.checkout.configuration.CheckoutInventoryProperties;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationCoordinator;
import com.shop.order.internal.checkout.service.OrderInventoryOrchestrationStateService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderInventoryReconciliationProcessor {

    static final String ITEM_METRIC = "shop.order.checkout.inventory.reconciliation.items";

    OrderInventoryOrchestrationStateService stateService;
    OrderInventoryReservationCoordinator coordinator;
    CheckoutInventoryProperties properties;
    MeterRegistry meterRegistry;

    public ReconciliationBatchResult reconcileBatch(Instant now) {
        Instant reconciliationTime = Objects.requireNonNull(now, "reconciliation time is required");
        var configuration = properties.getReconciliation();
        Instant staleBefore = reconciliationTime.minus(configuration.getStaleAfter());
        var candidates = stateService.findRecoveryCandidates(staleBefore, configuration.getBatchSize());

        int recovered = 0;
        int skipped = 0;
        int failed = 0;
        for (UUID eventId : candidates) {
            try {
                var recoveryPlan = stateService.prepareRecovery(eventId, staleBefore, reconciliationTime);
                if (recoveryPlan.isEmpty()) {
                    skipped++;
                    recordItem("skipped");
                    continue;
                }
                coordinator.recover(recoveryPlan.get());
                recovered++;
                recordItem("recovered");
            } catch (RuntimeException exception) {
                failed++;
                recordItem("failed");
                log.warn(
                        "Could not reconcile order inventory orchestration: eventId={}, errorType={}",
                        eventId,
                        exception.getClass().getSimpleName());
            }
        }
        return new ReconciliationBatchResult(candidates.size(), recovered, skipped, failed);
    }

    private void recordItem(String outcome) {
        meterRegistry.counter(ITEM_METRIC, "outcome", outcome).increment();
    }

    public record ReconciliationBatchResult(int selected, int recovered, int skipped, int failed) {

        public ReconciliationBatchResult {
            if (selected < 0
                    || recovered < 0
                    || skipped < 0
                    || failed < 0
                    || selected != recovered + skipped + failed) {
                throw new IllegalArgumentException("reconciliation batch result is inconsistent");
            }
        }
    }
}
