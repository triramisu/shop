package com.shop.payment.internal.service;

import com.shop.payment.event.PaymentStatusChangedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentLifecycleEventPublisher {

    ApplicationEventPublisher eventPublisher;

    public void publish(PaymentStatusChangedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
