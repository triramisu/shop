package com.shop.order.internal.checkout.service;

import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import com.shop.order.event.OrderStatusChangedEvent;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import com.shop.order.internal.checkout.orchestration.InventoryReservationLineStatus;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationLine;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationLinePlan;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationPlan;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import com.shop.order.internal.service.OrderLifecycleEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderInventoryOrchestrationStateService {

    OrderInventoryOrchestrationRepository orchestrationRepository;
    OrderLifecycleEventPublisher lifecycleEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OrderInventoryReservationPlan> claim(OrderInventoryReservationRequestedEvent event) {
        OrderInventoryOrchestration orchestration = load(event.eventId());
        if (!orchestration.claim(event.eventId(), event.correlationId(), event.orderId(), Instant.now())) {
            return Optional.empty();
        }
        orchestrationRepository.saveAndFlush(orchestration);
        List<OrderInventoryReservationLinePlan> pendingLines = orchestration.getLines().stream()
                .filter(line -> line.getStatus() == InventoryReservationLineStatus.PENDING
                        || line.getStatus() == InventoryReservationLineStatus.FAILED)
                .map(line -> new OrderInventoryReservationLinePlan(
                        line.getReservationId(), line.getProductVariantId(), line.getSku(), line.getQuantity()))
                .toList();
        return Optional.of(new OrderInventoryReservationPlan(
                orchestration.getRequestEventId(),
                orchestration.getCorrelationId(),
                orchestration.getOrder().getId(),
                orchestration.getExpiresAt(),
                pendingLines));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReserved(UUID eventId, UUID reservationId, UUID stockItemId) {
        OrderInventoryOrchestration orchestration = load(eventId);
        orchestration.markReserved(reservationId, stockItemId, Instant.now());
        orchestrationRepository.saveAndFlush(orchestration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLineFailed(UUID eventId, UUID reservationId, String failureCode) {
        OrderInventoryOrchestration orchestration = load(eventId);
        orchestration.markLineFailed(reservationId, failureCode, Instant.now());
        orchestrationRepository.saveAndFlush(orchestration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryRequired(UUID eventId, String failureCode) {
        OrderInventoryOrchestration orchestration = load(eventId);
        orchestration.markRetryRequired(failureCode, Instant.now());
        orchestrationRepository.saveAndFlush(orchestration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> beginCompensation(UUID eventId, String failureCode) {
        OrderInventoryOrchestration orchestration = load(eventId);
        orchestration.beginCompensation(failureCode, Instant.now());
        orchestrationRepository.saveAndFlush(orchestration);
        return reservationIds(orchestration, InventoryReservationLineStatus.RESERVED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReleased(UUID eventId, UUID reservationId) {
        OrderInventoryOrchestration orchestration = load(eventId);
        orchestration.markReleased(reservationId, Instant.now());
        orchestrationRepository.saveAndFlush(orchestration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> completeReserved(UUID eventId) {
        OrderInventoryOrchestration orchestration = load(eventId);
        orchestration.completeReserved(Instant.now());
        orchestrationRepository.saveAndFlush(orchestration);
        return reservationIds(orchestration, InventoryReservationLineStatus.RESERVED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeFailedAndCancel(UUID eventId) {
        OrderInventoryOrchestration orchestration = load(eventId);
        Instant occurredAt = Instant.now();
        orchestration.completeFailed(occurredAt);
        OrderStatusChangedEvent statusEvent = orchestration
                .getOrder()
                .transition(OrderTransitionEvent.INVENTORY_RESERVATION_FAILED, OrderTransitionActor.SYSTEM, occurredAt);
        orchestrationRepository.saveAndFlush(orchestration);
        lifecycleEventPublisher.publish(statusEvent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensationRequired(UUID eventId) {
        OrderInventoryOrchestration orchestration = load(eventId);
        orchestration.markCompensationRequired(Instant.now());
        orchestrationRepository.saveAndFlush(orchestration);
    }

    private OrderInventoryOrchestration load(UUID eventId) {
        return orchestrationRepository
                .findByRequestEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("inventory orchestration was not found"));
    }

    private List<UUID> reservationIds(
            OrderInventoryOrchestration orchestration, InventoryReservationLineStatus status) {
        return orchestration.getLines().stream()
                .filter(line -> line.getStatus() == status)
                .map(OrderInventoryReservationLine::getReservationId)
                .toList();
    }
}
