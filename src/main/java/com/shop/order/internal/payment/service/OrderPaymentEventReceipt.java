package com.shop.order.internal.payment.service;

record OrderPaymentEventReceipt(boolean processable) {

    static OrderPaymentEventReceipt process() {
        return new OrderPaymentEventReceipt(true);
    }

    static OrderPaymentEventReceipt skip() {
        return new OrderPaymentEventReceipt(false);
    }
}
