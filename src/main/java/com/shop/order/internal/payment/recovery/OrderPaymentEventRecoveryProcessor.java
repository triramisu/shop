package com.shop.order.internal.payment.recovery;

import com.shop.order.internal.payment.configuration.OrderPaymentProperties;
import com.shop.order.internal.payment.service.OrderPaymentEventCoordinator;
import com.shop.order.internal.payment.service.OrderPaymentEventInboxService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderPaymentEventRecoveryProcessor {

    static final String ITEM_METRIC = "shop.order.payment.recovery.items";

    OrderPaymentEventInboxService inboxService;
    OrderPaymentEventCoordinator coordinator;
    OrderPaymentProperties properties;
    MeterRegistry meterRegistry;

    public OrderPaymentEventRecoveryResult reconcileBatch(Instant now) {
        Instant staleBefore = now.minus(properties.getRecovery().getStaleAfter());
        var eventIds = inboxService.findRecoveryCandidates(
                staleBefore, properties.getRecovery().getBatchSize());
        int recovered = 0;
        int failed = 0;
        for (var eventId : eventIds) {
            try {
                coordinator.recover(eventId);
                recovered++;
                meterRegistry.counter(ITEM_METRIC, "outcome", "processed").increment();
            } catch (RuntimeException exception) {
                failed++;
                meterRegistry.counter(ITEM_METRIC, "outcome", "failed").increment();
                log.error("Order payment event recovery failed: eventId={}", eventId, exception);
            }
        }
        return new OrderPaymentEventRecoveryResult(eventIds.size(), recovered, failed);
    }
}
