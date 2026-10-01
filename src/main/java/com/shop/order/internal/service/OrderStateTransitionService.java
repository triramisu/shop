package com.shop.order.internal.service;

import com.shop.order.event.OrderStatusChangedEvent;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.entity.OrderTransitionRejectedException;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderStateTransitionService {

    CustomerOrderRepository orderRepository;
    OrderLifecycleEventPublisher lifecycleEventPublisher;

    @Transactional
    public OrderStatusChangedEvent transition(
            UUID orderId, OrderTransitionEvent transitionEvent, OrderTransitionActor actor) {
        CustomerOrder order =
                orderRepository.findById(orderId).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        try {
            OrderStatusChangedEvent event = order.transition(transitionEvent, actor, Instant.now());
            orderRepository.saveAndFlush(order);
            lifecycleEventPublisher.publish(event);
            return event;
        } catch (OrderTransitionRejectedException exception) {
            ErrorCode errorCode = exception.getReason() == OrderTransitionRejectedException.Reason.ACTOR
                    ? ErrorCode.ORDER_TRANSITION_ACTOR_FORBIDDEN
                    : ErrorCode.ORDER_STATE_TRANSITION_INVALID;
            throw new AppException(errorCode);
        } catch (OptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.ORDER_CONCURRENT_MODIFICATION);
        }
    }
}
