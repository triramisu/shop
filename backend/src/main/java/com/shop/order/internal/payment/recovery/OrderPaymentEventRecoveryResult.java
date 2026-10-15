package com.shop.order.internal.payment.recovery;

public record OrderPaymentEventRecoveryResult(int selected, int recovered, int failed) {}
