package com.shop.order.internal.payment.initiation;

import com.shop.order.event.OrderInventoryReservedEvent;
import com.shop.order.internal.payment.initiation.service.OrderPaymentInitiationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.order.payment",
        name = "auto-initiation-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class OrderPaymentInitiationEventListener {

    OrderPaymentInitiationService initiationService;

    @EventListener
    public void on(OrderInventoryReservedEvent event) {
        try {
            initiationService.initiate(event);
        } catch (RuntimeException exception) {
            log.error(
                    "Order payment initiation failed: eventId={}, correlationId={}, orderId={}",
                    event.eventId(),
                    event.correlationId(),
                    event.orderId(),
                    exception);
        }
    }
}
