package com.shop.payment.internal.service;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.processing.PaymentAttemptSnapshot;
import com.shop.payment.processing.PaymentInitiationCommand;
import com.shop.payment.processing.PaymentInitiationOperations;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderOperations;
import com.shop.payment.provider.PaymentProviderRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PaymentInitiationService implements PaymentInitiationOperations {

    private static final String IDEMPOTENCY_KEY_PREFIX = "payment-attempt-";

    private final List<PaymentProviderOperations> paymentProviderOperations;
    private final PaymentAttemptTransactionService transactionService;

    @Override
    public PaymentAttemptSnapshot initiate(PaymentInitiationCommand command) {
        PaymentProviderOperations provider = selectedProvider();
        Instant now = currentTime();
        PaymentAttemptSnapshot attempt = transactionService.getOrCreate(command, provider.providerCode(), now);
        if (!shouldInvokeProvider(attempt.status())) {
            return attempt;
        }

        PaymentProviderRequest providerRequest = new PaymentProviderRequest(
                attempt.id(),
                attempt.orderId(),
                attempt.attemptNumber(),
                attempt.amount(),
                attempt.currency(),
                IDEMPOTENCY_KEY_PREFIX + attempt.id());
        try {
            return transactionService.applyProviderResult(
                    attempt.id(), provider.createPayment(providerRequest), currentTime());
        } catch (PaymentProviderException exception) {
            return transactionService.applyProviderError(attempt.id(), exception, currentTime());
        }
    }

    private boolean shouldInvokeProvider(PaymentStatus status) {
        return status == PaymentStatus.CREATED || status == PaymentStatus.UNKNOWN;
    }

    private PaymentProviderOperations selectedProvider() {
        if (paymentProviderOperations.size() != 1) {
            throw new IllegalStateException("Exactly one payment provider must be configured");
        }
        return paymentProviderOperations.getFirst();
    }

    private Instant currentTime() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
