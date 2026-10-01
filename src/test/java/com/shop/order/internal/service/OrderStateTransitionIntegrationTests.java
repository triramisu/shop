package com.shop.order.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.order.event.OrderStatus;
import com.shop.order.event.OrderStatusChangedEvent;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
@Import(OrderStateTransitionIntegrationTests.FailingOrderEventListener.class)
class OrderStateTransitionIntegrationTests {

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private OrderStateTransitionService transitionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private FailingOrderEventListener failingEventListener;

    @BeforeEach
    @AfterEach
    void cleanCommittedOrderData() {
        failingEventListener.reset();
        jdbcTemplate.update("DELETE FROM don_hang_don_dat_hang");
    }

    @Test
    void persistsAValidTransitionThenPublishesExactlyOneDomainEvent() {
        CustomerOrder order = savePendingOrder("transition-owner");

        OrderStatusChangedEvent result = transitionService.transition(
                order.getId(), OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.SYSTEM);

        CustomerOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(result.orderId()).isEqualTo(order.getId());
        assertThat(applicationEvents.stream(OrderStatusChangedEvent.class)
                        .filter(event -> event.orderId().equals(order.getId())))
                .containsExactly(result);
    }

    @Test
    void rejectsAnInvalidTransitionWithoutChangingOrPublishing() {
        CustomerOrder order = savePendingOrder("invalid-owner");

        assertThatThrownBy(() -> transitionService.transition(
                        order.getId(), OrderTransitionEvent.SHIPMENT_DISPATCHED, OrderTransitionActor.SYSTEM))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_STATE_TRANSITION_INVALID));

        CustomerOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(applicationEvents.stream(OrderStatusChangedEvent.class)
                        .filter(event -> event.orderId().equals(order.getId())))
                .isEmpty();
    }

    @Test
    void rollsBackTheStateChangeWhenSynchronousEventDeliveryFails() {
        CustomerOrder order = savePendingOrder("event-failure-owner");
        failingEventListener.failNextEvent();

        assertThatThrownBy(() -> transitionService.transition(
                        order.getId(), OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.SYSTEM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated order event delivery failure");

        CustomerOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloaded.getVersion()).isZero();
    }

    private CustomerOrder savePendingOrder(String ownerSubject) {
        return orderRepository.saveAndFlush(
                CustomerOrder.createPending(ownerSubject, Instant.now().minusSeconds(10)));
    }

    @Component
    static class FailingOrderEventListener {

        private final AtomicBoolean failNext = new AtomicBoolean();

        void failNextEvent() {
            failNext.set(true);
        }

        void reset() {
            failNext.set(false);
        }

        @EventListener
        void on(OrderStatusChangedEvent event) {
            if (failNext.compareAndSet(true, false)) {
                throw new IllegalStateException("simulated order event delivery failure");
            }
        }
    }
}
