package com.shop.payment.internal.entity;

import com.shop.payment.event.PaymentStatus;
import java.util.EnumSet;
import java.util.Map;

final class PaymentStateMachine {

    private static final EnumSet<PaymentStatus> FROM_CREATED = EnumSet.of(
            PaymentStatus.PENDING,
            PaymentStatus.REQUIRES_ACTION,
            PaymentStatus.UNKNOWN,
            PaymentStatus.SUCCEEDED,
            PaymentStatus.FAILED,
            PaymentStatus.CANCELLED,
            PaymentStatus.EXPIRED);
    private static final EnumSet<PaymentStatus> FROM_PENDING = EnumSet.of(
            PaymentStatus.REQUIRES_ACTION,
            PaymentStatus.UNKNOWN,
            PaymentStatus.SUCCEEDED,
            PaymentStatus.FAILED,
            PaymentStatus.CANCELLED,
            PaymentStatus.EXPIRED);
    private static final EnumSet<PaymentStatus> FROM_REQUIRES_ACTION = EnumSet.of(
            PaymentStatus.PENDING,
            PaymentStatus.UNKNOWN,
            PaymentStatus.SUCCEEDED,
            PaymentStatus.FAILED,
            PaymentStatus.CANCELLED,
            PaymentStatus.EXPIRED);
    private static final EnumSet<PaymentStatus> FROM_UNKNOWN = EnumSet.of(
            PaymentStatus.PENDING,
            PaymentStatus.REQUIRES_ACTION,
            PaymentStatus.SUCCEEDED,
            PaymentStatus.FAILED,
            PaymentStatus.CANCELLED,
            PaymentStatus.EXPIRED);
    private static final Map<PaymentStatus, EnumSet<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.CREATED, FROM_CREATED,
            PaymentStatus.PENDING, FROM_PENDING,
            PaymentStatus.REQUIRES_ACTION, FROM_REQUIRES_ACTION,
            PaymentStatus.UNKNOWN, FROM_UNKNOWN);

    private PaymentStateMachine() {}

    static void requireTransition(PaymentStatus currentStatus, PaymentStatus targetStatus) {
        var targets = ALLOWED_TRANSITIONS.get(currentStatus);
        if (targets == null || !targets.contains(targetStatus)) {
            throw new IllegalStateException(
                    "payment transition is not allowed: " + currentStatus + " -> " + targetStatus);
        }
    }
}
