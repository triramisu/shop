package com.shop.order.internal.payment.service;

import com.shop.inventory.reservation.ConfirmStockReservationCommand;
import com.shop.inventory.reservation.ReleaseStockReservationCommand;
import com.shop.inventory.reservation.StockReservationOperations;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.order.event.OrderStatusChangedEvent;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import com.shop.order.internal.checkout.orchestration.InventoryReservationLineStatus;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import com.shop.order.internal.service.OrderLifecycleEventPublisher;
import com.shop.payment.event.PaymentStatus;
import com.shop.payment.event.PaymentStatusChangedEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class OrderPaymentOutcomeService {

    OrderInventoryOrchestrationRepository orchestrationRepository;
    StockReservationOperations reservationOperations;
    OrderLifecycleEventPublisher lifecycleEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    OrderPaymentApplyResult apply(PaymentStatusChangedEvent event) {
        OrderInventoryOrchestration orchestration = loadAndValidate(event);
        Instant handledAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return switch (event.currentStatus()) {
            case PENDING, REQUIRES_ACTION, UNKNOWN -> applyPending(orchestration, event, handledAt);
            case SUCCEEDED -> applySuccess(orchestration, event, handledAt);
            case FAILED, CANCELLED, EXPIRED -> applyFailure(orchestration, event, handledAt);
            case CREATED -> throw new OrderPaymentManualActionException("created payment status cannot be consumed");
        };
    }

    private OrderPaymentApplyResult applyPending(
            OrderInventoryOrchestration orchestration, PaymentStatusChangedEvent event, Instant handledAt) {
        if (!orchestration.recordPaymentPending(event.currentStatus(), event.occurredAt(), handledAt)) {
            return OrderPaymentApplyResult.IGNORED;
        }
        orchestrationRepository.saveAndFlush(orchestration);
        return OrderPaymentApplyResult.COMPLETED;
    }

    private OrderPaymentApplyResult applySuccess(
            OrderInventoryOrchestration orchestration, PaymentStatusChangedEvent event, Instant handledAt) {
        if (!orchestration.beginPaymentConfirmation(event.currentStatus(), event.occurredAt(), handledAt)) {
            return OrderPaymentApplyResult.IGNORED;
        }
        for (var line : orchestration.getLines()) {
            if (line.getStatus() == InventoryReservationLineStatus.CONFIRMED) {
                continue;
            }
            if (line.getStatus() != InventoryReservationLineStatus.RESERVED) {
                throw new OrderPaymentManualActionException("inventory line cannot be confirmed after payment");
            }
            var result = reservationOperations.confirm(new ConfirmStockReservationCommand(line.getReservationId()));
            if (result.status() != StockReservationStatus.CONFIRMED) {
                throw new OrderPaymentManualActionException("inventory reservation was not confirmed");
            }
            orchestration.markPaymentConfirmed(line.getReservationId(), handledAt);
        }
        orchestration.completePaymentConfirmation(handledAt);
        OrderStatusChangedEvent statusEvent = orchestration
                .getOrder()
                .transition(OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.SYSTEM, handledAt);
        orchestrationRepository.saveAndFlush(orchestration);
        lifecycleEventPublisher.publish(statusEvent);
        return OrderPaymentApplyResult.COMPLETED;
    }

    private OrderPaymentApplyResult applyFailure(
            OrderInventoryOrchestration orchestration, PaymentStatusChangedEvent event, Instant handledAt) {
        if (!orchestration.beginPaymentRelease(event.currentStatus(), event.occurredAt(), handledAt)) {
            return OrderPaymentApplyResult.IGNORED;
        }
        for (var line : orchestration.getLines()) {
            if (line.getStatus() == InventoryReservationLineStatus.RELEASED) {
                continue;
            }
            if (line.getStatus() != InventoryReservationLineStatus.RESERVED) {
                throw new OrderPaymentManualActionException("inventory line cannot be released after payment failure");
            }
            var result = reservationOperations.release(new ReleaseStockReservationCommand(line.getReservationId()));
            if (result.status() != StockReservationStatus.RELEASED
                    && result.status() != StockReservationStatus.EXPIRED) {
                throw new OrderPaymentManualActionException("inventory reservation was not released");
            }
            orchestration.markPaymentReleased(line.getReservationId(), handledAt);
        }
        orchestration.completePaymentRelease(handledAt);
        OrderStatusChangedEvent statusEvent = orchestration
                .getOrder()
                .transition(transitionFor(event.currentStatus()), OrderTransitionActor.SYSTEM, handledAt);
        orchestrationRepository.saveAndFlush(orchestration);
        lifecycleEventPublisher.publish(statusEvent);
        return OrderPaymentApplyResult.COMPLETED;
    }

    private OrderInventoryOrchestration loadAndValidate(PaymentStatusChangedEvent event) {
        OrderInventoryOrchestration orchestration = orchestrationRepository
                .findByOrderIdForUpdate(event.orderId())
                .orElseThrow(
                        () -> new OrderPaymentManualActionException("order inventory orchestration was not found"));
        if (!event.paymentAttemptId().equals(orchestration.getPaymentAttemptId())
                || event.amount().compareTo(orchestration.getOrder().getGrandTotal()) != 0
                || !event.currency().equals(orchestration.getOrder().getCurrency())) {
            throw new OrderPaymentManualActionException("payment event does not match order payment terms");
        }
        return orchestration;
    }

    private OrderTransitionEvent transitionFor(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case FAILED -> OrderTransitionEvent.PAYMENT_FAILED;
            case CANCELLED -> OrderTransitionEvent.PAYMENT_CANCELLED;
            case EXPIRED -> OrderTransitionEvent.PAYMENT_EXPIRED;
            default -> throw new IllegalArgumentException("payment status does not cancel an order");
        };
    }
}
