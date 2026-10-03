package com.shop.payment.internal.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.provider.stripe.StripeCheckoutStateAccess;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StripeCheckoutReturnServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void acceptsSignedReturnOnlyWhenSessionMatchesPersistedAttempt() {
        UUID attemptId = UUID.randomUUID();
        PaymentAttempt attempt = stripeAttempt(attemptId);
        StripeCheckoutStateAccess stateAccess = token -> attemptId;
        PaymentAttemptRepository repository = mock(PaymentAttemptRepository.class);
        when(repository.findById(attemptId)).thenReturn(Optional.of(attempt));
        StripeCheckoutReturnService service = new StripeCheckoutReturnService(stateAccess, repository);

        HostedCheckoutReturnResponse response = service.handleReturn("signed-state", "cs_test_session");

        assertThat(response.getPaymentAttemptId()).isEqualTo(attemptId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(response.getOutcome()).isEqualTo(CheckoutRedirectOutcome.RETURNED);
    }

    @Test
    void rejectsTamperedStateAndMismatchedSessionWithTheSamePublicError() {
        UUID attemptId = UUID.randomUUID();
        PaymentAttemptRepository repository = mock(PaymentAttemptRepository.class);
        when(repository.findById(attemptId)).thenReturn(Optional.of(stripeAttempt(attemptId)));
        StripeCheckoutReturnService mismatchService = new StripeCheckoutReturnService(token -> attemptId, repository);
        StripeCheckoutReturnService tamperedService = new StripeCheckoutReturnService(
                token -> {
                    throw new IllegalArgumentException("tampered");
                },
                repository);

        assertInvalidReturn(() -> mismatchService.handleReturn("signed-state", "cs_test_other"));
        assertInvalidReturn(() -> tamperedService.handleCancel("tampered-state"));
    }

    @Test
    void treatsCancelAsBrowserOutcomeWithoutMutatingPaymentState() {
        UUID attemptId = UUID.randomUUID();
        PaymentAttempt attempt = stripeAttempt(attemptId);
        PaymentAttemptRepository repository = mock(PaymentAttemptRepository.class);
        when(repository.findById(attemptId)).thenReturn(Optional.of(attempt));
        StripeCheckoutReturnService service = new StripeCheckoutReturnService(token -> attemptId, repository);

        HostedCheckoutReturnResponse response = service.handleCancel("signed-state");

        assertThat(response.getOutcome()).isEqualTo(CheckoutRedirectOutcome.CANCELLED);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
    }

    private PaymentAttempt stripeAttempt(UUID attemptId) {
        PaymentAttempt attempt =
                PaymentAttempt.start(attemptId, UUID.randomUUID(), 1, new BigDecimal("10.00"), "USD", "STRIPE", NOW);
        attempt.transition(
                PaymentStatus.REQUIRES_ACTION,
                "cs_test_session",
                URI.create("https://checkout.stripe.com/c/pay/session"),
                null,
                NOW.plusSeconds(1));
        return attempt;
    }

    private void assertInvalidReturn(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_CHECKOUT_RETURN_INVALID));
    }
}
