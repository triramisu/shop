package com.shop.order.internal.checkout.orchestration;

import com.shop.inventory.reservation.ReleaseStockReservationCommand;
import com.shop.inventory.reservation.ReserveStockCommand;
import com.shop.inventory.reservation.StockReservationOperations;
import com.shop.inventory.reservation.StockReservationResult;
import com.shop.inventory.reservation.StockReservationStatus;
import com.shop.inventory.reservation.StockReservationTarget;
import com.shop.inventory.reservation.StockReservationTargetOperations;
import com.shop.order.event.OrderInventoryCompensationEvent;
import com.shop.order.event.OrderInventoryCompensationStatus;
import com.shop.order.event.OrderInventoryReservationFailedEvent;
import com.shop.order.event.OrderInventoryReservationRequestedEvent;
import com.shop.order.event.OrderInventoryReservedEvent;
import com.shop.order.internal.checkout.configuration.CheckoutInventoryProperties;
import com.shop.order.internal.checkout.service.OrderInventoryOrchestrationStateService;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderInventoryReservationCoordinator {

    static final String UNEXPECTED_FAILURE = "UNEXPECTED_INVENTORY_FAILURE";

    OrderInventoryOrchestrationStateService stateService;
    StockReservationTargetOperations targetOperations;
    StockReservationOperations reservationOperations;
    CheckoutInventoryProperties properties;
    OrderInventoryEventPublisher eventPublisher;

    public void handle(OrderInventoryReservationRequestedEvent event) {
        stateService.claim(event).ifPresent(plan -> reserve(plan, event));
    }

    private void reserve(OrderInventoryReservationPlan plan, OrderInventoryReservationRequestedEvent event) {
        for (OrderInventoryReservationLinePlan line : plan.pendingLines()) {
            StockReservationResult result;
            try {
                StockReservationTarget target =
                        targetOperations.resolve(line.productVariantId(), line.sku(), properties.getLocationCode());
                result = reservationOperations.reserve(new ReserveStockCommand(
                        line.reservationId(), target.stockItemId(), line.quantity(), plan.expiresAt()));
            } catch (AppException exception) {
                failLineAndFinish(
                        event, line.reservationId(), exception.getErrorCode().name(), isRetryable(exception));
                return;
            } catch (RuntimeException exception) {
                failLineAndFinish(event, line.reservationId(), UNEXPECTED_FAILURE, true);
                return;
            }
            if (result.status() != StockReservationStatus.RESERVED) {
                failLineAndFinish(event, line.reservationId(), UNEXPECTED_FAILURE, true);
                return;
            }
            stateService.markReserved(plan.requestEventId(), line.reservationId(), result.stockItemId());
        }

        List<UUID> reservationIds = stateService.completeReserved(plan.requestEventId());
        eventPublisher.publishReserved(new OrderInventoryReservedEvent(
                UUID.randomUUID(),
                plan.requestEventId(),
                plan.correlationId(),
                plan.orderId(),
                reservationIds,
                Instant.now()));
    }

    private void failLineAndFinish(
            OrderInventoryReservationRequestedEvent event, UUID reservationId, String failureCode, boolean retryable) {
        stateService.markLineFailed(event.eventId(), reservationId, failureCode);
        if (retryable) {
            stateService.markRetryRequired(event.eventId(), failureCode);
            publishFailure(event, failureCode, true);
            return;
        }
        compensate(event, failureCode);
    }

    private void compensate(OrderInventoryReservationRequestedEvent event, String failureCode) {
        List<UUID> reservationsToRelease = stateService.beginCompensation(event.eventId(), failureCode);
        List<UUID> released = new ArrayList<>();
        List<UUID> unresolved = new ArrayList<>();
        for (UUID reservationId : reservationsToRelease) {
            try {
                var result = reservationOperations.release(new ReleaseStockReservationCommand(reservationId));
                if (result.status() != StockReservationStatus.RELEASED) {
                    throw new IllegalStateException("inventory release did not return RELEASED");
                }
                stateService.markReleased(event.eventId(), reservationId);
                released.add(reservationId);
            } catch (RuntimeException exception) {
                unresolved.add(reservationId);
            }
        }

        OrderInventoryCompensationStatus compensationStatus;
        if (unresolved.isEmpty()) {
            stateService.completeFailedAndCancel(event.eventId());
            compensationStatus = OrderInventoryCompensationStatus.COMPLETED;
        } else {
            stateService.markCompensationRequired(event.eventId());
            compensationStatus = OrderInventoryCompensationStatus.REQUIRED;
        }
        publishFailure(event, failureCode, false);
        eventPublisher.publishCompensation(new OrderInventoryCompensationEvent(
                UUID.randomUUID(),
                event.eventId(),
                event.correlationId(),
                event.orderId(),
                compensationStatus,
                failureCode,
                released,
                unresolved,
                Instant.now()));
    }

    private boolean isRetryable(AppException exception) {
        return exception.getErrorCode() == ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE
                || exception.getErrorCode() == ErrorCode.STOCK_RESERVATION_REPLAY_UNAVAILABLE;
    }

    private void publishFailure(OrderInventoryReservationRequestedEvent event, String failureCode, boolean retryable) {
        eventPublisher.publishFailed(new OrderInventoryReservationFailedEvent(
                UUID.randomUUID(),
                event.eventId(),
                event.correlationId(),
                event.orderId(),
                failureCode,
                retryable,
                Instant.now()));
    }
}
