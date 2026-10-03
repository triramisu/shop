package com.shop.payment.internal.checkout;

import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.provider.stripe.StripeCheckoutStateAccess;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "stripe")
@RequiredArgsConstructor
public class StripeCheckoutReturnService {

    private static final String STRIPE_PROVIDER_CODE = "STRIPE";

    private final StripeCheckoutStateAccess stateAccess;
    private final PaymentAttemptRepository paymentAttemptRepository;

    @Transactional(readOnly = true)
    public HostedCheckoutReturnResponse handleReturn(String state, String sessionId) {
        PaymentAttempt attempt = requireAttempt(state);
        if (!validSessionId(sessionId) || !sessionId.equals(attempt.getProviderReference())) {
            throw invalidReturn();
        }
        return response(attempt, CheckoutRedirectOutcome.RETURNED);
    }

    @Transactional(readOnly = true)
    public HostedCheckoutReturnResponse handleCancel(String state) {
        return response(requireAttempt(state), CheckoutRedirectOutcome.CANCELLED);
    }

    private PaymentAttempt requireAttempt(String state) {
        try {
            var paymentAttemptId = stateAccess.verify(state);
            PaymentAttempt attempt =
                    paymentAttemptRepository.findById(paymentAttemptId).orElseThrow(this::invalidReturn);
            if (!STRIPE_PROVIDER_CODE.equals(attempt.getProviderCode()) || attempt.getProviderReference() == null) {
                throw invalidReturn();
            }
            return attempt;
        } catch (IllegalArgumentException exception) {
            throw invalidReturn();
        }
    }

    private boolean validSessionId(String value) {
        return value != null && value.length() <= 255 && value.matches("cs_(test|live)_[A-Za-z0-9_]+$");
    }

    private HostedCheckoutReturnResponse response(PaymentAttempt attempt, CheckoutRedirectOutcome outcome) {
        return HostedCheckoutReturnResponse.builder()
                .paymentAttemptId(attempt.getId())
                .status(attempt.getStatus())
                .outcome(outcome)
                .build();
    }

    private AppException invalidReturn() {
        return new AppException(ErrorCode.PAYMENT_CHECKOUT_RETURN_INVALID);
    }
}
