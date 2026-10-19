package com.shop.order.internal.service;

import com.shop.order.event.OrderStatusChangedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderLifecycleEventPublisher {

    ApplicationEventPublisher eventPublisher;

    public void publish(OrderStatusChangedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
