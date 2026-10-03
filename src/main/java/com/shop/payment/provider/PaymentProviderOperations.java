package com.shop.payment.provider;

public interface PaymentProviderOperations {

    String providerCode();

    PaymentProviderResult createPayment(PaymentProviderRequest request);
}
