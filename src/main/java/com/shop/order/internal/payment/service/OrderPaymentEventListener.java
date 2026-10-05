package com.shop.order.internal.payment.service;

import com.shop.payment.event.PaymentStatusChangedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
        prefix = "app.order.payment",
        name = "event-consumption-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class OrderPaymentEventListener {

    OrderPaymentEventCoordinator coordinator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PaymentStatusChangedEvent event) {
        try {
            coordinator.handle(event);
        } catch (RuntimeException exception) {
            log.error(
                    "Order payment event listener stopped unexpectedly: eventId={}, paymentAttemptId={}, orderId={}",
                    event.eventId(),
                    event.paymentAttemptId(),
                    event.orderId(),
                    exception);
        }
    }
}
