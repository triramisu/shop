package com.shop.order.internal.checkout.service;

import com.shop.order.internal.checkout.mapper.OrderSnapshotMapper;
import com.shop.order.internal.checkout.pricing.CheckoutPricingResult;
import com.shop.order.internal.entity.CustomerOrder;
import com.shop.order.internal.repository.CustomerOrderRepository;
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

    @Transactional
    public UUID createFromCart(String ownerSubject, long expectedCartVersion) {
        CheckoutPricingResult pricing = checkoutPricingService.price(ownerSubject, expectedCartVersion);
        CustomerOrder order = CustomerOrder.createPending(
                ownerSubject, orderSnapshotMapper.toDrafts(pricing.breakdown()), pricing.pricedAt());
        return orderRepository.saveAndFlush(order).getId();
    }
}
