package com.shop.payment.internal.provider.fake;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderOperations;
import com.shop.payment.provider.PaymentProviderRequest;
import com.shop.payment.provider.PaymentProviderResult;
import com.shop.payment.provider.PaymentProviderStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class FakePaymentProvider implements PaymentProviderOperations {

    static final String PROVIDER_CODE = "FAKE";

    private final FakePaymentProviderProperties properties;
    private final FakePaymentProviderControls controls;
    private final Map<String, CachedOutcome> outcomes = new LinkedHashMap<>();

    FakePaymentProvider(FakePaymentProviderProperties properties, FakePaymentProviderControls controls) {
        this.properties = properties;
        this.controls = controls;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public synchronized PaymentProviderResult createPayment(PaymentProviderRequest request) {
        Objects.requireNonNull(request, "payment provider request is required");
        CachedOutcome cached = outcomes.get(request.idempotencyKey());
        if (cached != null) {
            if (!cached.request().equals(request)) {
                throw new PaymentProviderException(
                        PaymentProviderErrorType.INVALID_REQUEST, "FAKE_IDEMPOTENCY_KEY_REUSED");
            }
            return cached.replay();
        }
        if (outcomes.size() >= properties.getMaxStoredAttempts()) {
            throw new PaymentProviderException(
                    PaymentProviderErrorType.CONFIGURATION_ERROR, "FAKE_ATTEMPT_CAPACITY_EXHAUSTED");
        }

        CachedOutcome outcome = createOutcome(request, controls.modeFor(request.paymentAttemptId()));
        outcomes.put(request.idempotencyKey(), outcome);
        return outcome.replay();
    }

    private CachedOutcome createOutcome(PaymentProviderRequest request, FakePaymentMode mode) {
        String reference = FakePaymentIdentifiers.providerReference(request.idempotencyKey());
        return switch (mode) {
            case SUCCESS ->
                CachedOutcome.result(
                        request, new PaymentProviderResult(PaymentProviderStatus.SUCCEEDED, reference, null, null));
            case DECLINE ->
                CachedOutcome.result(
                        request,
                        new PaymentProviderResult(
                                PaymentProviderStatus.FAILED, reference, null, "FAKE_PAYMENT_DECLINED"));
            case PENDING ->
                CachedOutcome.result(
                        request, new PaymentProviderResult(PaymentProviderStatus.PENDING, reference, null, null));
            case TIMEOUT -> CachedOutcome.error(request, PaymentProviderErrorType.TIMEOUT, "FAKE_PROVIDER_TIMEOUT");
        };
    }

    private record CachedOutcome(
            PaymentProviderRequest request,
            PaymentProviderResult result,
            PaymentProviderErrorType errorType,
            String errorCode) {

        static CachedOutcome result(PaymentProviderRequest request, PaymentProviderResult result) {
            return new CachedOutcome(request, result, null, null);
        }

        static CachedOutcome error(
                PaymentProviderRequest request, PaymentProviderErrorType errorType, String errorCode) {
            return new CachedOutcome(request, null, errorType, errorCode);
        }

        PaymentProviderResult replay() {
            if (errorType != null) {
                throw new PaymentProviderException(errorType, errorCode);
            }
            return result;
        }
    }
}
