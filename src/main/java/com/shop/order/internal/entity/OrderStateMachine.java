package com.shop.order.internal.entity;

import com.shop.order.event.OrderStatus;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

final class OrderStateMachine {

    private static final Map<TransitionKey, TransitionRule> RULES = Map.ofEntries(
            rule(
                    OrderStatus.PENDING,
                    OrderTransitionEvent.PAYMENT_CONFIRMED,
                    OrderStatus.PAID,
                    OrderTransitionActor.SYSTEM),
            rule(
                    OrderStatus.PENDING,
                    OrderTransitionEvent.CANCELLED_BEFORE_PAYMENT,
                    OrderStatus.CANCELLED,
                    OrderTransitionActor.CUSTOMER,
                    OrderTransitionActor.STAFF,
                    OrderTransitionActor.SYSTEM),
            rule(
                    OrderStatus.PENDING,
                    OrderTransitionEvent.PAYMENT_EXPIRED,
                    OrderStatus.CANCELLED,
                    OrderTransitionActor.SYSTEM),
            rule(
                    OrderStatus.PAID,
                    OrderTransitionEvent.FULFILLMENT_STARTED,
                    OrderStatus.PROCESSING,
                    OrderTransitionActor.STAFF,
                    OrderTransitionActor.SYSTEM),
            rule(
                    OrderStatus.PAID,
                    OrderTransitionEvent.CANCELLATION_COMPENSATED,
                    OrderStatus.CANCELLED,
                    OrderTransitionActor.SYSTEM),
            rule(
                    OrderStatus.PROCESSING,
                    OrderTransitionEvent.CANCELLATION_COMPENSATED,
                    OrderStatus.CANCELLED,
                    OrderTransitionActor.SYSTEM),
            rule(
                    OrderStatus.PROCESSING,
                    OrderTransitionEvent.SHIPMENT_DISPATCHED,
                    OrderStatus.SHIPPED,
                    OrderTransitionActor.STAFF,
                    OrderTransitionActor.SYSTEM),
            rule(
                    OrderStatus.SHIPPED,
                    OrderTransitionEvent.DELIVERY_CONFIRMED,
                    OrderStatus.DELIVERED,
                    OrderTransitionActor.CUSTOMER,
                    OrderTransitionActor.STAFF,
                    OrderTransitionActor.SYSTEM));

    private OrderStateMachine() {}

    static OrderStatus transition(
            OrderStatus currentStatus, OrderTransitionEvent transitionEvent, OrderTransitionActor actor) {
        Objects.requireNonNull(currentStatus, "current status is required");
        Objects.requireNonNull(transitionEvent, "transition event is required");
        Objects.requireNonNull(actor, "transition actor is required");

        TransitionRule rule = RULES.get(new TransitionKey(currentStatus, transitionEvent));
        if (rule == null) {
            throw new OrderTransitionRejectedException(OrderTransitionRejectedException.Reason.STATE);
        }
        if (!rule.allowedActors().contains(actor)) {
            throw new OrderTransitionRejectedException(OrderTransitionRejectedException.Reason.ACTOR);
        }
        return rule.targetStatus();
    }

    private static Map.Entry<TransitionKey, TransitionRule> rule(
            OrderStatus sourceStatus,
            OrderTransitionEvent transitionEvent,
            OrderStatus targetStatus,
            OrderTransitionActor firstActor,
            OrderTransitionActor... remainingActors) {
        return Map.entry(
                new TransitionKey(sourceStatus, transitionEvent),
                new TransitionRule(targetStatus, EnumSet.of(firstActor, remainingActors)));
    }

    private record TransitionKey(OrderStatus sourceStatus, OrderTransitionEvent transitionEvent) {}

    private record TransitionRule(OrderStatus targetStatus, EnumSet<OrderTransitionActor> allowedActors) {}
}
