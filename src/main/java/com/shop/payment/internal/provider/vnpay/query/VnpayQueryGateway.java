package com.shop.payment.internal.provider.vnpay.query;

import com.shop.payment.provider.PaymentStatusQueryRequest;
import com.shop.payment.provider.PaymentStatusQueryResult;

@FunctionalInterface
interface VnpayQueryGateway {
    PaymentStatusQueryResult query(PaymentStatusQueryRequest request);
}
