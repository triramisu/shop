package com.shop.order.internal.checkout.service;

import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestrationSnapshot;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationLine;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationLineSnapshot;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderInventoryOrchestrationQueryService {

    OrderInventoryOrchestrationRepository orchestrationRepository;

    @Transactional(readOnly = true)
    public OrderInventoryOrchestrationSnapshot getByOrderId(UUID orderId) {
        return snapshot(load(orderId));
    }

    @Transactional(readOnly = true)
    public OrderInventoryReservationRequestedEvent getRequestedEvent(UUID orderId) {
        return load(orderId).toRequestedEvent();
    }

    private OrderInventoryOrchestration load(UUID orderId) {
        return orchestrationRepository
                .findByOrder_Id(orderId)
                .orElseThrow(() -> new IllegalStateException("inventory orchestration was not found"));
    }

    private OrderInventoryOrchestrationSnapshot snapshot(OrderInventoryOrchestration orchestration) {
        return new OrderInventoryOrchestrationSnapshot(
                orchestration.getId(),
                orchestration.getOrder().getId(),
                orchestration.getRequestEventId(),
                orchestration.getCorrelationId(),
                orchestration.getStatus(),
                orchestration.getExpiresAt(),
                orchestration.getFailureCode(),
                orchestration.getPaymentAttemptId(),
                orchestration.getPaymentAttemptNumber(),
                orchestration.getLastPaymentStatus(),
                orchestration.getLastPaymentEventAt(),
                orchestration.getVersion(),
                orchestration.getLines().stream().map(this::lineSnapshot).toList());
    }

    private OrderInventoryReservationLineSnapshot lineSnapshot(OrderInventoryReservationLine line) {
        return new OrderInventoryReservationLineSnapshot(
                line.getReservationId(),
                line.getOrderItemId(),
                line.getProductVariantId(),
                line.getLineNumber(),
                line.getSku(),
                line.getQuantity(),
                line.getStockItemId(),
                line.getStatus(),
                line.getFailureCode());
    }
}
