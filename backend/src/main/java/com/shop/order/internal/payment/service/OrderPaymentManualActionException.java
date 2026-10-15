package com.shop.order.internal.payment.service;

final class OrderPaymentManualActionException extends RuntimeException {

    OrderPaymentManualActionException(String message) {
        super(message);
    }
}
