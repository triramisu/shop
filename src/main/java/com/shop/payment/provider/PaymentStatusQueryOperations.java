package com.shop.payment.provider;

/** Optional server-side capability. A query never changes local payment or order state. */
public interface PaymentStatusQueryOperations {
    String providerCode();

    PaymentStatusQueryResult query(PaymentStatusQueryRequest request);
}
