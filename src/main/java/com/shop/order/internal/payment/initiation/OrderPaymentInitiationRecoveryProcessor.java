package com.shop.order.internal.payment.initiation;

import com.shop.order.internal.payment.configuration.OrderPaymentProperties;
import com.shop.order.internal.payment.initiation.service.OrderPaymentInitiationService;
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
public class OrderPaymentInitiationRecoveryProcessor {

    static final String ITEM_METRIC = "shop.order.payment.initiation.recovery.items";

    OrderPaymentInitiationService initiationService;
    OrderPaymentProperties properties;
    MeterRegistry meterRegistry;

    public OrderPaymentInitiationRecoveryResult reconcileBatch(Instant now) {
        Instant staleBefore = now.minus(properties.getRecovery().getStaleAfter());
        var orderIds = initiationService.findRecoveryCandidates(
                staleBefore, properties.getRecovery().getBatchSize());
        int initiated = 0;
        int failed = 0;
        for (var orderId : orderIds) {
            try {
                if (initiationService.initiate(orderId)) {
                    initiated++;
                    meterRegistry.counter(ITEM_METRIC, "outcome", "initiated").increment();
                } else {
                    meterRegistry.counter(ITEM_METRIC, "outcome", "skipped").increment();
                }
            } catch (RuntimeException exception) {
                failed++;
                meterRegistry.counter(ITEM_METRIC, "outcome", "failed").increment();
                log.error("Order payment initiation recovery failed: orderId={}", orderId, exception);
            }
        }
        return new OrderPaymentInitiationRecoveryResult(orderIds.size(), initiated, failed);
    }
}
