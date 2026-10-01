package com.shop.order.internal.checkout.service;

import com.shop.order.internal.checkout.configuration.CheckoutInventoryProperties;
import com.shop.order.internal.checkout.mapper.OrderSnapshotMapper;
import com.shop.order.internal.checkout.orchestration.OrderInventoryEventPublisher;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import com.shop.order.internal.checkout.pricing.CheckoutPricingResult;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.repository.CustomerOrderRepository;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderCreationService {

    CheckoutPricingService checkoutPricingService;
    OrderSnapshotMapper orderSnapshotMapper;
    CustomerOrderRepository orderRepository;
    OrderInventoryOrchestrationRepository orchestrationRepository;
    CheckoutInventoryProperties inventoryProperties;
    OrderInventoryEventPublisher inventoryEventPublisher;

    @Transactional
    public UUID createFromCart(String ownerSubject, long expectedCartVersion) {
        return createFromCart(ownerSubject, expectedCartVersion, UUID.randomUUID());
    }

    @Transactional
    public UUID createFromCart(String ownerSubject, long expectedCartVersion, UUID correlationId) {
        CheckoutPricingResult pricing = checkoutPricingService.price(ownerSubject, expectedCartVersion);
        CustomerOrder order = CustomerOrder.createPending(
                ownerSubject, orderSnapshotMapper.toDrafts(pricing.breakdown()), pricing.pricedAt());
        CustomerOrder savedOrder = orderRepository.saveAndFlush(order);
        Instant requestedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OrderInventoryOrchestration orchestration = OrderInventoryOrchestration.start(
                savedOrder,
                UUID.randomUUID(),
                Objects.requireNonNull(correlationId, "correlation id is required"),
                requestedAt.plus(inventoryProperties.getReservationDuration()),
                requestedAt);
        orchestrationRepository.saveAndFlush(orchestration);
        inventoryEventPublisher.publishRequested(orchestration.toRequestedEvent());
        return savedOrder.getId();
    }
}
