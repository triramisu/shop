package com.shop.payment.internal.provider.fake;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class FakePaymentProviderControls {

    private final FakePaymentProviderProperties properties;
    private final Map<UUID, FakePaymentMode> modes = new ConcurrentHashMap<>();

    FakePaymentProviderControls(FakePaymentProviderProperties properties) {
        this.properties = properties;
    }

    synchronized void useMode(UUID paymentAttemptId, FakePaymentMode mode) {
        Objects.requireNonNull(paymentAttemptId, "payment attempt id is required");
        Objects.requireNonNull(mode, "fake payment mode is required");
        if (!modes.containsKey(paymentAttemptId) && modes.size() >= properties.getMaxStoredAttempts()) {
            throw new IllegalStateException("Fake payment control capacity is exhausted");
        }
        modes.put(paymentAttemptId, mode);
    }

    FakePaymentMode modeFor(UUID paymentAttemptId) {
        return modes.getOrDefault(paymentAttemptId, properties.getDefaultMode());
    }

    synchronized void clear() {
        modes.clear();
    }
}
