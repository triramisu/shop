package com.shop.order.internal.payment.initiation.service;

import com.shop.payment.processing.PaymentInitiationCommand;
import java.util.Objects;
import java.util.UUID;

record OrderPaymentInitiationPlan(UUID orderId, PaymentInitiationCommand command) {

    OrderPaymentInitiationPlan {
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(command, "payment initiation command is required");
        if (!orderId.equals(command.orderId())) {
            throw new IllegalArgumentException("payment command does not belong to the order");
        }
    }
}
