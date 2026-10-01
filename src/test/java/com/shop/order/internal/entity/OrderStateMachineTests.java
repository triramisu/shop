package com.shop.order.internal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.order.event.OrderStatus;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderStateMachineTests {

    private static final Map<TransitionKey, ExpectedRule> EXPECTED_RULES = Map.ofEntries(
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

    @Test
    void enforcesTheCompleteTransitionAndActorMatrix() {
        for (OrderStatus status : OrderStatus.values()) {
            for (OrderTransitionEvent event : OrderTransitionEvent.values()) {
                for (OrderTransitionActor actor : OrderTransitionActor.values()) {
                    assertTransition(status, event, actor);
                }
            }
        }
    }

    @Test
    void rejectsNullTransitionInputs() {
        assertThatThrownBy(() -> OrderStateMachine.transition(
                        null, OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.SYSTEM))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OrderStateMachine.transition(OrderStatus.PENDING, null, OrderTransitionActor.SYSTEM))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        OrderStateMachine.transition(OrderStatus.PENDING, OrderTransitionEvent.PAYMENT_CONFIRMED, null))
                .isInstanceOf(NullPointerException.class);
    }

    private void assertTransition(OrderStatus status, OrderTransitionEvent event, OrderTransitionActor actor) {
        ExpectedRule expectedRule = EXPECTED_RULES.get(new TransitionKey(status, event));
        if (expectedRule == null) {
            assertRejected(status, event, actor, OrderTransitionRejectedException.Reason.STATE);
        } else if (!expectedRule.allowedActors().contains(actor)) {
            assertRejected(status, event, actor, OrderTransitionRejectedException.Reason.ACTOR);
        } else {
            assertThat(OrderStateMachine.transition(status, event, actor)).isEqualTo(expectedRule.targetStatus());
        }
    }

    private void assertRejected(
            OrderStatus status,
            OrderTransitionEvent event,
            OrderTransitionActor actor,
            OrderTransitionRejectedException.Reason reason) {
        assertThatThrownBy(() -> OrderStateMachine.transition(status, event, actor))
                .isInstanceOfSatisfying(
                        OrderTransitionRejectedException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(reason));
    }

    private static Map.Entry<TransitionKey, ExpectedRule> rule(
            OrderStatus sourceStatus,
            OrderTransitionEvent transitionEvent,
            OrderStatus targetStatus,
            OrderTransitionActor firstActor,
            OrderTransitionActor... remainingActors) {
        return Map.entry(
                new TransitionKey(sourceStatus, transitionEvent),
                new ExpectedRule(targetStatus, EnumSet.of(firstActor, remainingActors)));
    }

    private record TransitionKey(OrderStatus sourceStatus, OrderTransitionEvent transitionEvent) {}

    private record ExpectedRule(OrderStatus targetStatus, EnumSet<OrderTransitionActor> allowedActors) {}
}
