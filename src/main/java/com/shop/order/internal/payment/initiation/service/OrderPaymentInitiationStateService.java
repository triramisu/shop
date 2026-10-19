package com.shop.order.internal.payment.initiation.service;

import com.shop.order.event.OrderInventoryReservedEvent;
import com.shop.order.event.OrderStatus;
import com.shop.order.internal.checkout.orchestration.InventoryOrchestrationStatus;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import com.shop.payment.processing.PaymentInitiationCommand;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class OrderPaymentInitiationStateService {

    static final int INITIAL_ATTEMPT_NUMBER = 1;

    OrderInventoryOrchestrationRepository orchestrationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<OrderPaymentInitiationPlan> prepare(OrderInventoryReservedEvent event) {
        OrderInventoryOrchestration orchestration = orchestrationRepository
                .findByOrderIdForUpdate(event.orderId())
                .orElseThrow(() -> new IllegalStateException("order inventory orchestration was not found"));
        if (!orchestration.getRequestEventId().equals(event.requestEventId())
                || !orchestration.getCorrelationId().equals(event.correlationId())) {
            throw new IllegalArgumentException("reserved inventory event does not match its order orchestration");
        }
        return prepare(orchestration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<OrderPaymentInitiationPlan> prepare(UUID orderId) {
        return prepare(orchestrationRepository
                .findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("order inventory orchestration was not found")));
    }

    @Transactional(readOnly = true)
    java.util.List<UUID> findRecoveryCandidates(Instant staleBefore, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("payment initiation recovery batch size must be positive");
        }
        return orchestrationRepository.findPaymentInitiationCandidateOrderIds(
                InventoryOrchestrationStatus.RESERVED,
                java.util.Objects.requireNonNull(staleBefore, "payment initiation stale cutoff is required"),
                PageRequest.of(0, batchSize));
    }

    private Optional<OrderPaymentInitiationPlan> prepare(OrderInventoryOrchestration orchestration) {
        if (orchestration.getOrder().getStatus() != OrderStatus.PENDING
                || (orchestration.getStatus() != InventoryOrchestrationStatus.RESERVED
                        && orchestration.getStatus() != InventoryOrchestrationStatus.PAYMENT_PENDING
                        && orchestration.getStatus() != InventoryOrchestrationStatus.PAYMENT_RECOVERY_REQUIRED)) {
            return Optional.empty();
        }

        Instant preparedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID attemptId =
                orchestration.getPaymentAttemptId() == null ? UUID.randomUUID() : orchestration.getPaymentAttemptId();
        int attemptNumber = orchestration.getPaymentAttemptNumber() == null
                ? INITIAL_ATTEMPT_NUMBER
                : orchestration.getPaymentAttemptNumber();
        orchestration.assignPaymentAttempt(attemptId, attemptNumber, preparedAt);
        orchestrationRepository.saveAndFlush(orchestration);

        PaymentInitiationCommand command = new PaymentInitiationCommand(
                attemptId,
                orchestration.getOrder().getId(),
                attemptNumber,
                orchestration.getOrder().getGrandTotal(),
                orchestration.getOrder().getCurrency());
        return Optional.of(
                new OrderPaymentInitiationPlan(orchestration.getOrder().getId(), command));
    }
}
