package com.shop.order.internal.checkout.orchestration;

import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderInventoryReservationEventListener {

    OrderInventoryReservationCoordinator coordinator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderInventoryReservationRequestedEvent event) {
        try {
            coordinator.handle(event);
        } catch (RuntimeException exception) {
            log.error(
                    "Inventory orchestration stopped unexpectedly: eventId={}, correlationId={}, orderId={}",
                    event.eventId(),
                    event.correlationId(),
                    event.orderId(),
                    exception);
        }
    }
}
