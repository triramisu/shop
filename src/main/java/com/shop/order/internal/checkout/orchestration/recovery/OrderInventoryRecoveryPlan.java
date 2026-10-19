package com.shop.order.internal.checkout.orchestration.recovery;

import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationPlan;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderInventoryRecoveryPlan(
        InventoryOrchestrationRecoveryAction action,
        OrderInventoryReservationRequestedEvent requestedEvent,
        OrderInventoryReservationPlan reservationPlan,
        String failureCode,
        List<UUID> reservationIds) {

    public OrderInventoryRecoveryPlan {
        Objects.requireNonNull(action, "recovery action is required");
        Objects.requireNonNull(requestedEvent, "inventory reservation event is required");
        reservationIds = List.copyOf(Objects.requireNonNull(reservationIds, "reservation ids are required"));
        if (action == InventoryOrchestrationRecoveryAction.RETRY_RESERVATION) {
            Objects.requireNonNull(reservationPlan, "reservation retry plan is required");
            if (failureCode != null || !reservationIds.isEmpty()) {
                throw new IllegalArgumentException("reservation retry must not contain compensation data");
            }
        } else {
            if (reservationPlan != null) {
                throw new IllegalArgumentException("compensation retry must not contain a reservation plan");
            }
            if (failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException("compensation failure code is required");
            }
            failureCode = failureCode.strip();
        }
    }

    public static OrderInventoryRecoveryPlan retry(
            OrderInventoryReservationRequestedEvent event, OrderInventoryReservationPlan reservationPlan) {
        return new OrderInventoryRecoveryPlan(
                InventoryOrchestrationRecoveryAction.RETRY_RESERVATION, event, reservationPlan, null, List.of());
    }

    public static OrderInventoryRecoveryPlan compensate(
            OrderInventoryReservationRequestedEvent event, String failureCode, List<UUID> reservationIds) {
        return new OrderInventoryRecoveryPlan(
                InventoryOrchestrationRecoveryAction.RETRY_COMPENSATION, event, null, failureCode, reservationIds);
    }
}
