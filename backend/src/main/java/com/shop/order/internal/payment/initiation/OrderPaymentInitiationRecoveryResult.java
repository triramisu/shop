package com.shop.order.internal.payment.initiation;

public record OrderPaymentInitiationRecoveryResult(int selected, int initiated, int failed) {}
