package com.shop.order.internal.entity;

import static com.shop.order.support.OrderTestFixtures.itemDraft;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.order.event.OrderStatus;
import com.shop.order.event.OrderTransitionActor;
import com.shop.order.event.OrderTransitionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerOrderDomainTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void createsANormalizedPendingOrder() {
        CustomerOrder order = CustomerOrder.createPending("  Buyer-01  ", List.of(itemDraft()), CREATED_AT);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getOwnerSubject()).isEqualTo("buyer-01");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getStatusChangedAt()).isEqualTo(CREATED_AT);
        assertThat(order.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(order.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(order.getVersion()).isZero();
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getLineNumber()).isEqualTo(1);
            assertThat(item.getSku()).isEqualTo("TEST-SKU-01");
        });
        assertThat(order.getCurrency()).isEqualTo("USD");
        assertThat(order.getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(order.getDiscount()).isEqualByComparingTo("2.00");
        assertThat(order.getTax()).isEqualByComparingTo("1.44");
        assertThat(order.getGrandTotal()).isEqualByComparingTo("19.44");
    }

    @Test
    void changesStateAndCreatesANonPiiDomainEvent() {
        CustomerOrder order = CustomerOrder.createPending("buyer-01", List.of(itemDraft()), CREATED_AT);
        Instant occurredAt = CREATED_AT.plusSeconds(60);

        var event = order.transition(OrderTransitionEvent.PAYMENT_CONFIRMED, OrderTransitionActor.SYSTEM, occurredAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatusChangedAt()).isEqualTo(occurredAt);
        assertThat(order.getUpdatedAt()).isEqualTo(occurredAt);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.previousStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(event.currentStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(event.transitionEvent()).isEqualTo(OrderTransitionEvent.PAYMENT_CONFIRMED);
        assertThat(event.actor()).isEqualTo(OrderTransitionActor.SYSTEM);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void leavesTheAggregateUnchangedWhenATransitionIsRejected() {
        CustomerOrder order = CustomerOrder.createPending("buyer-01", List.of(itemDraft()), CREATED_AT);

        assertThatThrownBy(() -> order.transition(
                        OrderTransitionEvent.PAYMENT_CONFIRMED,
                        OrderTransitionActor.CUSTOMER,
                        CREATED_AT.plusSeconds(60)))
                .isInstanceOfSatisfying(
                        OrderTransitionRejectedException.class, exception -> assertThat(exception.getReason())
                                .isEqualTo(OrderTransitionRejectedException.Reason.ACTOR));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getStatusChangedAt()).isEqualTo(CREATED_AT);
        assertThat(order.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void protectsTheLifecycleTimelineAndRequiredOwner() {
        assertThatThrownBy(() -> CustomerOrder.createPending(" ", List.of(itemDraft()), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustomerOrder.createPending("buyer-01", List.of(), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        CustomerOrder order = CustomerOrder.createPending("buyer-01", List.of(itemDraft()), CREATED_AT);

        assertThatThrownBy(() -> order.transition(
                        OrderTransitionEvent.PAYMENT_EXPIRED, OrderTransitionActor.SYSTEM, CREATED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void rejectsMixedCurrenciesWithoutMutatingTheProvidedDrafts() {
        OrderItemSnapshotDraft euroDraft = new OrderItemSnapshotDraft(
                java.util.UUID.randomUUID(),
                "TEST-EUR-01",
                "Sản phẩm EUR",
                1,
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("5.00"),
                "EUR");

        assertThatThrownBy(() -> CustomerOrder.createPending("buyer-01", List.of(itemDraft(), euroDraft), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one currency");
    }
}
