package com.shop.order.internal.checkout.orchestration;

import com.shop.order.event.OrderInventoryCompensationEvent;
import com.shop.order.event.OrderInventoryReservationFailedEvent;
import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import com.shop.order.event.OrderInventoryReservedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderInventoryEventPublisher {

    ApplicationEventPublisher eventPublisher;

    public void publishRequested(OrderInventoryReservationRequestedEvent event) {
        eventPublisher.publishEvent(event);
    }

    public void publishReserved(OrderInventoryReservedEvent event) {
        eventPublisher.publishEvent(event);
    }

    public void publishFailed(OrderInventoryReservationFailedEvent event) {
        eventPublisher.publishEvent(event);
    }

    public void publishCompensation(OrderInventoryCompensationEvent event) {
        eventPublisher.publishEvent(event);
    }
}
