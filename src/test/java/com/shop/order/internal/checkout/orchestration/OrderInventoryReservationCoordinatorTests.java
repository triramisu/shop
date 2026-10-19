package com.shop.order.internal.checkout.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.shop.order.event.OrderInventoryReservationRequestedLine;
import com.shop.order.internal.checkout.configuration.CheckoutInventoryProperties;
import com.shop.order.internal.checkout.service.OrderInventoryOrchestrationStateService;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderInventoryReservationCoordinatorTests {

    private OrderInventoryOrchestrationStateService stateService;
    private StockReservationTargetOperations targetOperations;
    private StockReservationOperations reservationOperations;
    private OrderInventoryEventPublisher eventPublisher;
    private OrderInventoryReservationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        stateService = mock(OrderInventoryOrchestrationStateService.class);
        targetOperations = mock(StockReservationTargetOperations.class);
        reservationOperations = mock(StockReservationOperations.class);
        eventPublisher = mock(OrderInventoryEventPublisher.class);
        coordinator = new OrderInventoryReservationCoordinator(
                stateService,
                targetOperations,
                reservationOperations,
                new CheckoutInventoryProperties(),
                eventPublisher);
    }

    @Test
    void marksATemporaryInventoryFailureForSafeRetryWithoutCompensating() {
        Scenario scenario = scenario(1);
        var line = scenario.plan().pendingLines().getFirst();
        when(stateService.claim(scenario.event())).thenReturn(Optional.of(scenario.plan()));
        when(targetOperations.resolve(line.productVariantId(), line.sku(), "MAIN"))
                .thenReturn(target(line));
        when(reservationOperations.reserve(any(ReserveStockCommand.class)))
                .thenThrow(new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE));

        coordinator.handle(scenario.event());

        verify(stateService)
                .markLineFailed(
                        scenario.event().eventId(),
                        line.reservationId(),
                        ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE.name());
        verify(stateService)
                .markRetryRequired(scenario.event().eventId(), ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE.name());
        verify(stateService, never()).beginCompensation(any(), any());
        ArgumentCaptor<OrderInventoryReservationFailedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderInventoryReservationFailedEvent.class);
        verify(eventPublisher).publishFailed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().retryable()).isTrue();
    }

    @Test
    void exposesAnUnresolvedReservationWhenCompensationFails() {
        Scenario scenario = scenario(2);
        var first = scenario.plan().pendingLines().get(0);
        var second = scenario.plan().pendingLines().get(1);
        UUID firstStockItemId = UUID.randomUUID();
        when(stateService.claim(scenario.event())).thenReturn(Optional.of(scenario.plan()));
        when(targetOperations.resolve(any(), any(), eq("MAIN")))
                .thenAnswer(invocation -> new StockReservationTarget(
                        invocation.<UUID>getArgument(0).equals(first.productVariantId())
                                ? firstStockItemId
                                : UUID.randomUUID(),
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        "MAIN"));
        when(reservationOperations.reserve(any(ReserveStockCommand.class)))
                .thenReturn(reserved(first, firstStockItemId, scenario.plan().expiresAt()))
                .thenThrow(new AppException(ErrorCode.INVENTORY_INSUFFICIENT_STOCK));
        when(stateService.beginCompensation(scenario.event().eventId(), ErrorCode.INVENTORY_INSUFFICIENT_STOCK.name()))
                .thenReturn(List.of(first.reservationId()));
        when(reservationOperations.release(new ReleaseStockReservationCommand(first.reservationId())))
                .thenThrow(new AppException(ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE));

        coordinator.handle(scenario.event());

        verify(stateService).markReserved(scenario.event().eventId(), first.reservationId(), firstStockItemId);
        verify(stateService)
                .markLineFailed(
                        scenario.event().eventId(),
                        second.reservationId(),
                        ErrorCode.INVENTORY_INSUFFICIENT_STOCK.name());
        verify(stateService).markCompensationRequired(scenario.event().eventId());
        verify(stateService, never()).completeFailedAndCancel(any());
        verify(eventPublisher).publishFailed(any(OrderInventoryReservationFailedEvent.class));
        ArgumentCaptor<OrderInventoryCompensationEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderInventoryCompensationEvent.class);
        verify(eventPublisher).publishCompensation(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(OrderInventoryCompensationStatus.REQUIRED);
        assertThat(eventCaptor.getValue().unresolvedReservationIds()).containsExactly(first.reservationId());
    }

    private Scenario scenario(int lineCount) {
        Instant occurredAt = Instant.parse("2026-10-02T00:00:00Z");
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        List<OrderInventoryReservationLinePlan> plans = java.util.stream.IntStream.range(0, lineCount)
                .mapToObj(index -> new OrderInventoryReservationLinePlan(
                        UUID.randomUUID(), UUID.randomUUID(), "SKU-" + index, index + 1L))
                .toList();
        OrderInventoryReservationPlan plan =
                new OrderInventoryReservationPlan(eventId, correlationId, orderId, occurredAt.plusSeconds(900), plans);
        OrderInventoryReservationRequestedEvent event = new OrderInventoryReservationRequestedEvent(
                eventId,
                correlationId,
                orderId,
                plan.expiresAt(),
                plans.stream()
                        .map(line -> new OrderInventoryReservationRequestedLine(
                                line.reservationId(),
                                line.reservationId(),
                                line.productVariantId(),
                                line.sku(),
                                line.quantity()))
                        .toList(),
                occurredAt);
        return new Scenario(event, plan);
    }

    private StockReservationTarget target(OrderInventoryReservationLinePlan line) {
        return new StockReservationTarget(UUID.randomUUID(), line.productVariantId(), line.sku(), "MAIN");
    }

    private StockReservationResult reserved(
            OrderInventoryReservationLinePlan line, UUID stockItemId, Instant expiresAt) {
        return new StockReservationResult(
                line.reservationId(),
                stockItemId,
                line.quantity(),
                StockReservationStatus.RESERVED,
                expiresAt,
                10,
                line.quantity(),
                10 - line.quantity());
    }

    private record Scenario(OrderInventoryReservationRequestedEvent event, OrderInventoryReservationPlan plan) {}
}
