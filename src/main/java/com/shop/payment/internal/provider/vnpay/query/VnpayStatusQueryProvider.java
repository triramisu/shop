package com.shop.payment.internal.provider.vnpay.query;

import com.shop.payment.internal.provider.vnpay.VnpayPaymentProvider;
import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentStatusQueryOperations;
import com.shop.payment.provider.PaymentStatusQueryRequest;
import com.shop.payment.provider.PaymentStatusQueryResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Objects;
import java.util.concurrent.Semaphore;

final class VnpayStatusQueryProvider implements PaymentStatusQueryOperations {
    private final VnpayQueryGateway gateway;
    private final CircuitBreaker breaker;
    private final Semaphore permits = new Semaphore(2);

    VnpayStatusQueryProvider(VnpayQueryGateway gateway, CircuitBreaker breaker) {
        this.gateway = gateway;
        this.breaker = breaker;
    }

    @Override
    public String providerCode() {
        return VnpayPaymentProvider.CODE;
    }

    @Override
    public PaymentStatusQueryResult query(PaymentStatusQueryRequest request) {
        Objects.requireNonNull(request, "payment query request is required");
        if (!permits.tryAcquire()) {
            throw new PaymentProviderException(PaymentProviderErrorType.TEMPORARY_UNAVAILABLE, "VNPAY_QUERY_BUSY");
        }
        try {
            return breaker.executeSupplier(() -> gateway.query(request));
        } catch (CallNotPermittedException exception) {
            throw new PaymentProviderException(
                    PaymentProviderErrorType.TEMPORARY_UNAVAILABLE, "VNPAY_QUERY_CIRCUIT_OPEN");
        } finally {
            permits.release();
        }
    }
}
