package com.shop.payment.internal.provider.fake;

import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.RefundProviderOperations;
import com.shop.payment.provider.RefundProviderRequest;
import com.shop.payment.provider.RefundProviderResult;
import com.shop.payment.provider.RefundProviderStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "fake")
public class FakeRefundProvider implements RefundProviderOperations {
    public enum Mode {
        SUCCESS,
        DECLINE,
        PENDING,
        TIMEOUT
    }

    private static final int CAPACITY = 10_000;
    private final Map<UUID, Mode> modes = new HashMap<>();
    private final Map<String, RefundProviderRequest> requests = new HashMap<>();
    private final Map<String, Mode> outcomes = new HashMap<>();
    private final Map<UUID, Integer> calls = new HashMap<>();

    public synchronized void useMode(UUID paymentAttemptId, Mode mode) {
        if (modes.size() >= CAPACITY && !modes.containsKey(paymentAttemptId)) {
            throw new IllegalStateException("Kho điều khiển refund fake đã đầy");
        }
        modes.put(java.util.Objects.requireNonNull(paymentAttemptId), java.util.Objects.requireNonNull(mode));
    }

    @Override
    public String providerCode() {
        return "FAKE";
    }

    @Override
    public synchronized RefundProviderResult refund(RefundProviderRequest request) {
        RefundProviderRequest prior = requests.get(request.idempotencyKey());
        if (prior != null && !prior.equals(request)) {
            throw new PaymentProviderException(PaymentProviderErrorType.INVALID_REQUEST, "FAKE_REFUND_KEY_CONFLICT");
        }
        if (prior == null) {
            if (requests.size() >= CAPACITY) {
                throw new PaymentProviderException(
                        PaymentProviderErrorType.CONFIGURATION_ERROR, "FAKE_REFUND_CAPACITY");
            }
            requests.put(request.idempotencyKey(), request);
            outcomes.put(request.idempotencyKey(), modes.getOrDefault(request.paymentAttemptId(), Mode.SUCCESS));
        }
        calls.merge(request.paymentAttemptId(), 1, Integer::sum);
        String reference = "fake_refund_" + request.refundId();
        return switch (outcomes.get(request.idempotencyKey())) {
            case SUCCESS -> new RefundProviderResult(RefundProviderStatus.SUCCEEDED, reference, null);
            case DECLINE -> new RefundProviderResult(RefundProviderStatus.FAILED, reference, "FAKE_REFUND_DECLINED");
            case PENDING -> new RefundProviderResult(RefundProviderStatus.PENDING, reference, null);
            case TIMEOUT -> throw new PaymentProviderException(PaymentProviderErrorType.TIMEOUT, "FAKE_REFUND_TIMEOUT");
        };
    }

    public synchronized int invocationCount(UUID paymentAttemptId) {
        return calls.getOrDefault(paymentAttemptId, 0);
    }
}
