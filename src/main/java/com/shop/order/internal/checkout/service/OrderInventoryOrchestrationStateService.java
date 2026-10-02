package com.shop.order.internal.checkout.service;

import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import com.shop.order.event.OrderStatusChangedEvent;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import com.shop.order.internal.checkout.orchestration.InventoryOrchestrationStatus;
import com.shop.order.internal.checkout.orchestration.InventoryReservationLineStatus;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationLine;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationLinePlan;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationPlan;
import com.shop.order.internal.checkout.orchestration.recovery.OrderInventoryRecoveryPlan;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import com.shop.order.internal.service.OrderLifecycleEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderInventoryOrchestrationStateService {

    static final String INTERRUPTED_PROCESSING = "ORCHESTRATION_PROCESSING_INTERRUPTED";
    static final String INTERRUPTED_COMPENSATION = "ORCHESTRATION_COMPENSATION_INTERRUPTED";
    static final List<InventoryOrchestrationStatus> RECOVERABLE_STATUSES = List.of(
            InventoryOrchestrationStatus.REQUESTED,
            InventoryOrchestrationStatus.PROCESSING,
            InventoryOrchestrationStatus.RETRY_REQUIRED,
            InventoryOrchestrationStatus.COMPENSATING,
            InventoryOrchestrationStatus.COMPENSATION_REQUIRED);

    OrderInventoryOrchestrationRepository orchestrationRepository;
    OrderLifecycleEventPublisher lifecycleEventPublisher;

    @Transactional(readOnly = true)
    public List<UUID> findRecoveryCandidates(Instant staleBefore, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("recovery batch size must be positive");
        }
        return orchestrationRepository.findRecoveryCandidateRequestEventIds(
                RECOVERABLE_STATUSES,
                java.util.Objects.requireNonNull(staleBefore, "stale cutoff is required"),
                PageRequest.of(0, batchSize));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OrderInventoryRecoveryPlan> prepareRecovery(
            UUID eventId, Instant staleBefore, Instant recoveredAt) {
        OrderInventoryOrchestration orchestration = load(eventId);
        if (orchestration.getUpdatedAt().isAfter(staleBefore)) {
            return Optional.empty();
        }

        return switch (orchestration.getStatus()) {
            case REQUESTED, RETRY_REQUIRED -> Optional.of(claimForRecovery(orchestration, recoveredAt));
            case PROCESSING -> {
                orchestration.markRetryRequired(INTERRUPTED_PROCESSING, recoveredAt);
                yield Optional.of(claimForRecovery(orchestration, recoveredAt));
            }
            case COMPENSATING, COMPENSATION_REQUIRED -> {
                orchestration.resumeCompensation(recoveredAt);
                orchestrationRepository.saveAndFlush(orchestration);
                String failureCode = orchestration.getFailureCode() == null
                        ? INTERRUPTED_COMPENSATION
                        : orchestration.getFailureCode();
                yield Optional.of(OrderInventoryRecoveryPlan.compensate(
                        orchestration.toRequestedEvent(),
                        failureCode,
                        reservationIds(orchestration, InventoryReservationLineStatus.RESERVED)));
            }
            case RESERVED, FAILED -> Optional.empty();
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OrderInventoryReservationPlan> claim(OrderInventoryReservationRequestedEvent event) {
        OrderInventoryOrchestration orchestration = load(event.eventId());
        if (!orchestration.claim(event.eventId(), event.correlationId(), event.orderId(), Instant.now())) {
            return Optional.empty();
        }
        orchestrationRepository.saveAndFlush(orchestration);
        return Optional.of(reservationPlan(orchestration));
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

    private OrderInventoryRecoveryPlan claimForRecovery(
            OrderInventoryOrchestration orchestration, Instant recoveredAt) {
        var event = orchestration.toRequestedEvent();
        if (!orchestration.claim(event.eventId(), event.correlationId(), event.orderId(), recoveredAt)) {
            throw new IllegalStateException("recoverable inventory orchestration could not be claimed");
        }
        orchestrationRepository.saveAndFlush(orchestration);
        return OrderInventoryRecoveryPlan.retry(event, reservationPlan(orchestration));
    }

    private OrderInventoryReservationPlan reservationPlan(OrderInventoryOrchestration orchestration) {
        List<OrderInventoryReservationLinePlan> pendingLines = orchestration.getLines().stream()
                .filter(line -> line.getStatus() == InventoryReservationLineStatus.PENDING
                        || line.getStatus() == InventoryReservationLineStatus.FAILED)
                .map(line -> new OrderInventoryReservationLinePlan(
                        line.getReservationId(), line.getProductVariantId(), line.getSku(), line.getQuantity()))
                .toList();
        return new OrderInventoryReservationPlan(
                orchestration.getRequestEventId(),
                orchestration.getCorrelationId(),
                orchestration.getOrder().getId(),
                orchestration.getExpiresAt(),
                pendingLines);
    }
}
