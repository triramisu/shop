package com.shop.order.internal.payment.initiation.service;

import com.shop.order.event.OrderInventoryReservedEvent;
import com.shop.payment.processing.PaymentInitiationOperations;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderPaymentInitiationService {

    OrderPaymentInitiationStateService stateService;
    PaymentInitiationOperations paymentInitiationOperations;

    public boolean initiate(OrderInventoryReservedEvent event) {
        return stateService.prepare(event).map(this::initiate).orElse(false);
    }

    public boolean initiate(UUID orderId) {
        return stateService.prepare(orderId).map(this::initiate).orElse(false);
    }

    public List<UUID> findRecoveryCandidates(Instant staleBefore, int batchSize) {
        return stateService.findRecoveryCandidates(staleBefore, batchSize);
    }

    private boolean initiate(OrderPaymentInitiationPlan plan) {
        paymentInitiationOperations.initiate(plan.command());
        return true;
    }
}
