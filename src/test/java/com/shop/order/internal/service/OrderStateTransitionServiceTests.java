package com.shop.order.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class OrderStateTransitionServiceTests {

    private CustomerOrderRepository orderRepository;
    private OrderLifecycleEventPublisher eventPublisher;
    private OrderStateTransitionService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(CustomerOrderRepository.class);
        eventPublisher = mock(OrderLifecycleEventPublisher.class);
        service = new OrderStateTransitionService(orderRepository, eventPublisher);
    }

    @Test
    void mapsAStaleWriteToTheOrderConcurrencyContractWithoutPublishingAnEvent() {
        CustomerOrder order =
                CustomerOrder.createPending("buyer-01", Instant.now().minusSeconds(10));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order))
                .thenThrow(new ObjectOptimisticLockingFailureException(CustomerOrder.class, order.getId()));

        assertThatThrownBy(() -> service.transition(
                        order.getId(), OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.SYSTEM))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_CONCURRENT_MODIFICATION));

        verify(eventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mapsUnknownOrderAndRejectedActorToStableErrors() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(
                        unknownId, OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.SYSTEM))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));

        CustomerOrder order =
                CustomerOrder.createPending("buyer-01", Instant.now().minusSeconds(10));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.transition(
                        order.getId(), OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.CUSTOMER))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_TRANSITION_ACTOR_FORBIDDEN));
    }
}
