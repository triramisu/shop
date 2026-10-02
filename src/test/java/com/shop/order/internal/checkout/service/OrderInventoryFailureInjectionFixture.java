package com.shop.order.internal.checkout.service;

import com.shop.order.internal.checkout.configuration.CheckoutInventoryProperties;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.entity.OrderItemSnapshotDraft;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderInventoryFailureInjectionFixture {

    CustomerOrderRepository orderRepository;
    OrderInventoryOrchestrationRepository orchestrationRepository;
    CheckoutInventoryProperties inventoryProperties;

    @Transactional
    public UUID createWithoutPublishingEvent(String ownerSubject, UUID productVariantId, String sku, int quantity) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant requestedAt = now.minusSeconds(60);
        BigDecimal unitPrice = new BigDecimal("25.00");
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        var draft = new OrderItemSnapshotDraft(
                productVariantId,
                sku,
                "Lost event product",
                quantity,
                unitPrice,
                subtotal,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                subtotal,
                "USD");
        CustomerOrder order =
                orderRepository.saveAndFlush(CustomerOrder.createPending(ownerSubject, List.of(draft), requestedAt));
        OrderInventoryOrchestration orchestration = OrderInventoryOrchestration.start(
                order,
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.plus(inventoryProperties.getReservationDuration()),
                requestedAt);
        orchestrationRepository.saveAndFlush(orchestration);
        return order.getId();
    }
}
