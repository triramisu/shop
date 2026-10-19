package com.shop.payment.provider;

public interface RefundProviderOperations {
    String providerCode();

    RefundProviderResult refund(RefundProviderRequest request);
}
