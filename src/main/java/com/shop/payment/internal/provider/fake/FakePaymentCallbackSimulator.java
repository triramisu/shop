package com.shop.payment.internal.provider.fake;

import com.shop.payment.provider.PaymentProviderRequest;
import com.shop.payment.provider.PaymentProviderResult;
import com.shop.payment.provider.PaymentProviderStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

final class FakePaymentCallbackSimulator {

    private final FakePaymentProvider provider;
    private final FakePaymentProviderProperties properties;

    FakePaymentCallbackSimulator(FakePaymentProvider provider, FakePaymentProviderProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    List<FakePaymentCallback> simulate(PaymentProviderRequest request, int deliveryCount) {
        if (deliveryCount < 1 || deliveryCount > properties.getMaxCallbackDeliveries()) {
            throw new IllegalArgumentException("fake callback delivery count is invalid");
        }
        PaymentProviderResult result = provider.createPayment(request);
        PaymentProviderStatus callbackStatus =
                result.status() == PaymentProviderStatus.PENDING ? PaymentProviderStatus.SUCCEEDED : result.status();
        FakePaymentCallback callback = new FakePaymentCallback(
                FakePaymentIdentifiers.providerEventId(result.providerReference()),
                result.providerReference(),
                callbackStatus,
                result.failureCode(),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        return Collections.nCopies(deliveryCount, callback);
    }
}
